package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.util.PathNormalizer;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChunkReceiverService {

    private static final Logger log = LoggerFactory.getLogger(ChunkReceiverService.class);
    private static final int BUFFER_SIZE = 8192;

    private final FileTransferRepository transferRepository;
    private final NexusConfig nexusConfig;
    private final TransferLogService transferLogService;
    private final AgentService agentService;

    /**
     * Called by target agent when source initiates a transfer.
     * Creates the FileTransfer record and prepares the partial file on disk.
     */
    @Transactional
    public void prepareReceive(FileTransfer transfer) {

        if (transfer.getFileSize() <= 0) {
            throw new FileTransferException("Invalid file size: " + transfer.getFileSize(), null);
        }

        Path partialFile = resolvePartialPath(transfer.getFileName());

        try {
            Files.createDirectories(partialFile.getParent());

            // Pre-allocate the file
            try (RandomAccessFile raf = new RandomAccessFile(partialFile.toFile(), "rw")) {
                raf.setLength(transfer.getFileSize());
            }

        } catch (IOException e) {

            throw new FileTransferException("Failed to prepare file on disk", e);
        }

        transferRepository.save(transfer);
        log.info("Prepared to receive: {} ({} bytes)", transfer.getFileName(), transfer.getFileSize());
    }

    /**
     * Called on each incoming chunk PATCH request.
     * Writes bytes at the correct offset, updates confirmedOffset in DB.
     * Reject chunks for non-active and out of order transfers
     */
    @Transactional
    public ChunkAckResponse receiveChunk(UUID transferId, long offset, InputStream chunkStream) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.ACTIVE) {
            throw new FileTransferException(
                    "Transfer is not active: " + transfer.getStatus(), null
            );
        }

        if (offset != transfer.getConfirmedOffset()) {
            throw new FileTransferException(
                    "Unexpected offset. Expected: " + transfer.getConfirmedOffset() + " got: " + offset, null
            );
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
        Path finalPath = Paths.get(nexusConfig.getUploadDirectory())
                .resolve(transfer.getFileName());
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
        return Paths.get(nexusConfig.getPartialDirectory()).resolve(fileName + ".part");
    }

    /**
     * Log completed transfer using existing LogService
     */
    private void logCompletedTransfer(FileTransfer transfer) {
        try {
            transferLogService.logTransfer(
                    transfer.getSourceAgentId(),
                    null,
                    transfer.getFileName(),
                    transfer.getFileSize()
            );
        } catch (Exception e) {
            log.warn("Failed to log completed transfer, file was saved successfully", e);
        }
    }

    private void deletePartialFile(String fileName) {
        try {
            Files.deleteIfExists(
                    Paths.get(nexusConfig.getPartialDirectory()).resolve(fileName + ".part")
            );
        } catch (IOException e) {
            log.warn("Could not delete partial file for: {}", fileName);
        }
    }
}