package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.agent.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.config.AgentConfig;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChunkReceiverService {

    private static final Logger log = LoggerFactory.getLogger(ChunkReceiverService.class);
    private static final int BUFFER_SIZE = 8192;

    private final FileTransferRepository transferRepository;
    private final DirectoryTransferRepository directoryTransferRepository;
    private final BatchTransferRepository batchTransferRepository;
    private final AgentConfig agentConfig;
    private final LogService logService;
    private final AgentService agentService;

    /**
     * Called by target agent when source initiates a transfer.
     * Creates the FileTransfer record and prepares the partial file on disk.
     */
    @Transactional
    public void prepareReceive(FileTransfer transfer) {

        if (transfer.getFileSize() <= 0) {
            throw new FileTransferException(
                    "Invalid file size: " + transfer.getFileSize(), null);
        }

        Path partialFile = resolvePartialPath(transfer.getFileName());
        allocatePartialFile(partialFile, transfer.getFileSize());

        transferRepository.save(transfer);
        log.info("Prepared to receive: {} ({} bytes)",
                transfer.getFileName(), transfer.getFileSize());
    }

    /**
     * Called by target when source registers a directory transfer.
     * Creates DirectoryTransfer record, empty dirs on disk,
     * allocates partial files, saves all in one batch.
     */
    @Transactional
    public void prepareReceiveDirectory(ReceiveDirectoryRequest request) {
        DirectoryTransfer dt = DirectoryTransfer.initiate(
                request.getDirectoryTransferId(),
                request.getSourceAgentId(),
                null,   // targetAgentId unknown on receiver side
                null,               //targetIp, this is the target
                0,                  // targetPort, this is the target
                request.getDirectoryName(),
                null,          // sourcePath is unknown on receiver side
                request.getTotalFiles(),
                request.getTotalSize(),
                request.getEmptyDirectories() != null
                        ? request.getEmptyDirectories()
                        : Collections.emptyList()
        );
        dt.setStatus(GroupTransferStatus.ACTIVE);
        directoryTransferRepository.save(dt);

        /* create empty directories before any file chunks arrive */
        Path uploadsDir = Paths.get(agentConfig.getUploadDirectory());
        Path dirRoot = uploadsDir.resolve(request.getDirectoryName());

        for (String emptyDir : dt.getEmptyDirectories()) {
            try {
                Files.createDirectories(dirRoot.resolve(emptyDir));
            } catch (IOException e) {
                throw new FileTransferException(
                        "Failed to create empty directory: " + emptyDir, e);
            }
        }

        /* build and allocate all child file transfers */
        List<FileTransfer> fileTransfers = new ArrayList<>();

        for (ReceiveDirectoryRequest.FileEntry entry : request.getFiles()) {
            if (entry.getFileSize() <= 0) {
                throw new FileTransferException(
                        "Invalid file size for: " + entry.getFileName(), null);
            }

            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceAgentId(),
                    null,
                    entry.getFileName(),
                    null,
                    entry.getFileSize()
            );
            ft.setDirectoryTransferId(request.getDirectoryTransferId());
            ft.setRelativePath(entry.getRelativePath());
            ft.setDirectoryName(request.getDirectoryName());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = resolvePartialPath(entry.getFileName());
            allocatePartialFile(partialFile, entry.getFileSize());

            fileTransfers.add(ft);
        }

        transferRepository.saveAll(fileTransfers);

        log.info("Prepared to receive directory: {} ({} files)",
                request.getDirectoryName(), fileTransfers.size());
    }

    /**
     * Called by target when source registers a batch transfer.
     * Creates BatchTransfer record, allocates partial files, saves all in one batch.
     */
    @Transactional
    public void prepareReceiveBatch(ReceiveBatchRequest request) {
        BatchTransfer bt = BatchTransfer.initiate(
                request.getBatchTransferId(),
                request.getSourceAgentId(),
                null,          /* targetAgentId unknown on receiver side */
                null,          /* targetIp — this is the target */
                0,             /* targetPort — this is the target */
                request.getTotalFiles(),
                request.getTotalSize()
        );
        bt.setStatus(GroupTransferStatus.ACTIVE);
        batchTransferRepository.save(bt);

        List<FileTransfer> fileTransfers = new ArrayList<>();

        for (ReceiveBatchRequest.FileEntry entry : request.getFiles()) {
            if (entry.getFileSize() <= 0) {
                throw new FileTransferException(
                        "Invalid file size for: " + entry.getFileName(), null);
            }

            FileTransfer ft = FileTransfer.initiate(
                    entry.getTransferId(),
                    request.getSourceAgentId(),
                    null,
                    entry.getFileName(),
                    null,
                    entry.getFileSize()
            );
            ft.setBatchTransferId(request.getBatchTransferId());
            ft.setStatus(TransferStatus.ACTIVE);

            Path partialFile = resolvePartialPath(entry.getFileName());
            allocatePartialFile(partialFile, entry.getFileSize());

            fileTransfers.add(ft);
        }

        transferRepository.saveAll(fileTransfers);

        log.info("Prepared to receive batch: {} ({} files)",
                request.getBatchTransferId(), fileTransfers.size());
    }


    /**
     * Called on each incoming chunk PATCH request.
     * Writes bytes at the correct offset, updates confirmedOffset in DB.
     * Reject chunks for non-active and out of order transfers
     */
    @Transactional
    public ChunkAckResponse receiveChunk(UUID transferId, long offset, InputStream chunkStream) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.ACTIVE) {
            throw new FileTransferException(
                    "Transfer is not active: " + transfer.getStatus(), null);
        }

        if (offset != transfer.getConfirmedOffset()) {
            throw new FileTransferException(
                    "Unexpected offset. Expected: "
                            + transfer.getConfirmedOffset() + " got: " + offset, null);
        }

        Path partialFile = resolvePartialPath(transfer.getFileName());
        long bytesWritten = writeChunkToDisk(partialFile, offset, chunkStream);
        long newOffset = offset + bytesWritten;

        transfer.setConfirmedOffset(newOffset);
        transfer.setLastChunkAt(Instant.now());

        boolean complete = newOffset >= transfer.getFileSize();

        if (complete) {
            transfer.setStatus(TransferStatus.COMPLETED);
            moveToFinalLocation(transfer, partialFile);
            logCompletedTransfer(transfer);
            log.info("Transfer completed: {}", transfer.getFileName());
        }

        transferRepository.save(transfer);

        return new ChunkAckResponse(newOffset, complete);
    }

    /**
     * Allocates the partial file on disk at exact size, no data written yet
     */
    private void allocatePartialFile(Path partialFile, long fileSize) {
        try {
            Files.createDirectories(partialFile.getParent());
            try (RandomAccessFile raf = new RandomAccessFile(partialFile.toFile(), "rw")) {
                raf.setLength(fileSize);
            }
        } catch (IOException e) {
            throw new FileTransferException(
                    "Failed to allocate partial file: " + partialFile, e);
        }
    }

    /**
     * Writes chunk bytes to disk at the given offset using RandomAccessFile.
     */
    private long writeChunkToDisk(Path filePath, long offset, InputStream chunkStream) {
        long bytesWritten = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "rw")) {
            raf.seek(offset);

            int bytesRead;
            while ((bytesRead = chunkStream.read(buffer)) != -1) {
                raf.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }

        } catch (IOException e) {
            throw new FileTransferException("Failed to write chunk at offset " + offset, e);
        }

        return bytesWritten;
    }

    /**
     * Moves the completed partial file to the final upload directory.
     */
    private void moveToFinalLocation(FileTransfer transfer, Path partialFile) {
        Path uploadsDir = Paths.get(agentConfig.getUploadDirectory());

        Path finalPath;

        if (transfer.getRelativePath() != null && transfer.getDirectoryName() != null) {

            // For directory transfer, rebuild folder structure under directoryName/
            finalPath = uploadsDir
                    .resolve(transfer.getDirectoryName())
                    .resolve(transfer.getRelativePath());
        } else {
            finalPath = uploadsDir.resolve(transfer.getFileName());
        }

        try {
            Files.createDirectories(finalPath.getParent());
            Files.move(partialFile, finalPath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new FileTransferException("Failed to move completed file to upload directory", e);
        }
    }

    /**
     * Partial files live in a separate directory during transfer.
     * Only move to uploads/ on completion.
     */
    private Path resolvePartialPath(String fileName) {
        return Paths.get(agentConfig.getPartialDirectory()).resolve(fileName + ".part");
    }

    /**
     * Log completed transfer using existing LogService
     */
    private void logCompletedTransfer(FileTransfer transfer) {
        try {
            com.atamanahmet.beamlink.agent.domain.TransferLog transferLog =
                    new com.atamanahmet.beamlink.agent.domain.TransferLog();
            transferLog.setFilename(transfer.getFileName());
            transferLog.setFileSize(transfer.getFileSize());
            transferLog.setFromAgentId(transfer.getSourceAgentId());
            transferLog.setToAgentId(agentService.getAgentId());
            transferLog.setToAgentName(agentService.getAgentName());
            logService.logTransfer(transferLog);
        } catch (Exception e) {
            log.warn("Failed to log completed transfer, file was saved successfully", e);
        }
    }

    private void deletePartialFile(String fileName) {
        try {
            Files.deleteIfExists(
                    Paths.get(agentConfig.getPartialDirectory()).resolve(fileName + ".part")
            );
        } catch (IOException e) {
            log.warn("Could not delete partial file for: {}", fileName);
        }
    }
}