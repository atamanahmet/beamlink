package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.InsufficientDiskSpaceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileTransferService {

    private final Logger log = LoggerFactory.getLogger(FileTransferService.class);
    private final NexusConfig config;
    private final TransferLogService transferLogService;

    private static final int  READ_BUFFER_SIZE  = 8192;
    private static final int  WRITE_BUFFER_SIZE = 65536;
    private static final long DISK_BUFFER_BYTES = 100L * 1024 * 1024;

    public long receiveFileStream(
            InputStream inputStream,
            String filename,
            long fileSize,
            UUID fromAgentId,
            String fromAgentName) {

        validateFilename(filename);

        Path uploadDir = Paths.get(config.getUploadDirectory());
        Path finalPath = uploadDir.resolve(filename);
        Path tmpPath   = uploadDir.resolve(filename + ".tmp");

        // Directory must exist before getFileStore can inspect it
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new FileTransferException("Failed to create upload directory", e);
        }

        checkDiskSpace(uploadDir, fileSize);

        long bytesWritten = 0;
        byte[] buffer = new byte[READ_BUFFER_SIZE];

        try (BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(tmpPath.toFile()), WRITE_BUFFER_SIZE)) {

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }
            out.flush();

        } catch (IOException e) {
            try { Files.deleteIfExists(tmpPath); }
            catch (IOException cleanup) {
                log.warn("Failed to clean up temp file {}: {}", tmpPath, cleanup.getMessage());
            }
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("no space left")) {
                throw new InsufficientDiskSpaceException("No space left on device");
            }
            throw new FileTransferException("Failed to write file to disk", e);
        }

        try {
            Files.move(tmpPath, finalPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmpPath); }
            catch (IOException cleanup) {
                log.warn("Failed to clean up temp file after failed move: {}", cleanup.getMessage());
            }
            throw new FileTransferException("Failed to finalize file after transfer", e);
        }

        try {
            transferLogService.logTransfer(fromAgentId, fromAgentName, filename, bytesWritten);
        } catch (Exception e) {
            log.warn("Transfer succeeded but logging failed: {}", e.getMessage());
        }

        log.info("File received: {} ({} bytes) from {}", filename, bytesWritten, fromAgentName);
        return bytesWritten;
    }

    public boolean checkDiskSpaceAvailable(long requiredBytes) {
        try {
            Path uploadDir = Paths.get(config.getUploadDirectory());
            Files.createDirectories(uploadDir);
            checkDiskSpace(uploadDir, requiredBytes);
            return true;
        } catch (InsufficientDiskSpaceException e) {
            log.warn("Disk space check failed: {}", e.getMessage());
            return false;
        } catch (IOException e) {
            log.warn("Could not verify upload directory for disk check: {}", e.getMessage());
            return false;
        }
    }

    private void checkDiskSpace(Path directory, long requiredBytes) {
        try {
            FileStore store  = Files.getFileStore(directory);
            long usable      = store.getUsableSpace();
            long required    = requiredBytes + DISK_BUFFER_BYTES;
            if (usable < required) {
                throw new InsufficientDiskSpaceException(
                        String.format("Insufficient disk space. Required: %d MB, Available: %d MB",
                                required / (1024 * 1024),
                                usable   / (1024 * 1024))
                );
            }
        } catch (InsufficientDiskSpaceException e) {
            throw e;
        } catch (IOException e) {
            log.warn("Unable to check disk space: {}", e.getMessage());
        }
    }

    private void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new FileTransferException("Invalid filename: cannot be empty", null);
        }
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new FileTransferException("Invalid filename: contains path separators", null);
        }
        if (filename.contains("\0")) {
            throw new FileTransferException("Invalid filename: contains null bytes", null);
        }
    }
}