package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.exception.InsufficientDiskSpaceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Service
@RequiredArgsConstructor
public class FileTransferService {

    private final AgentConfig config;
    private final LogService logService;
    private final AgentInfoService agentInfoService;

    private final Logger log = LoggerFactory.getLogger(FileTransferService.class);

    /**
     * Receive file stream - DIRECT WRITE to disk as bytes arrive
     * No temp files, no double-write penalty
     */
    public long receiveFileStream(
            InputStream inputStream,
            String filename,
            long fileSize,
            String fromAgentId,
            String fromName) {

        // Validate
        validateFilename(filename);

        Path uploadDir = Paths.get(config.getUploadDirectory());
        Path filepath = uploadDir.resolve(filename);

        // Check disk space BEFORE starting write
        checkDiskSpace(uploadDir, fileSize);

        // Ensure directory exists
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new FileTransferException("Failed to create upload directory", e);
        }

        // Stream directly to disk with buffering for performance
        long bytesWritten = 0;
        byte[] buffer = new byte[8192]; // 8KB buffer for optimal performance

        try (BufferedOutputStream outputStream = new BufferedOutputStream(
                new FileOutputStream(filepath.toFile()), 65536)) { // 64KB output buffer

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesWritten += bytesRead;
            }

            outputStream.flush();

        } catch (IOException e) {
            // Clean up partial file on failure
            try {
                Files.deleteIfExists(filepath);
            } catch (IOException cleanupError) {
                log.warn("Failed to clean up partial file: {}", cleanupError.getMessage());
            }

            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("no space left")) {
                throw new InsufficientDiskSpaceException("No space left on device");
            }
            throw new FileTransferException("Failed to write file to disk", e);
        }

        // Log the transfer
        try {
            TransferLog transferLog = new TransferLog();

           if(!"Nexus".equals(fromName)){
               transferLog.setFromAgentId(fromAgentId != null ? fromAgentId : "Unknown");
               transferLog.setFromAgentName(fromName != null ? fromName : "Unknown");
               transferLog.setToAgentId(agentInfoService.getAgentId());
               transferLog.setToAgentName(agentInfoService.getAgentName());
               transferLog.setFilename(filename);
               transferLog.setFileSize(bytesWritten);

               logService.logTransfer(transferLog);
           }

            log.info("File received: {} ({} bytes) from {}",
                    filename, bytesWritten, fromName);
        } catch (Exception e) {
            log.warn("Failed to log transfer, but file was saved successfully", e);
        }

        return bytesWritten;
    }

    /**
     * Validate filename for security
     */
    private void validateFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new FileTransferException("Invalid filename: cannot be empty", null);
        }

        // Path traversal check
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new FileTransferException("Invalid filename: contains path separators", null);
        }

        // Null byte check (security)
        if (filename.contains("\0")) {
            throw new FileTransferException("Invalid filename: contains null bytes", null);
        }
    }

    /**
     * Check if there's enough disk space for the file
     */
    private void checkDiskSpace(Path directory, long requiredBytes) {
        try {
            FileStore store = Files.getFileStore(directory);
            long usableSpace = store.getUsableSpace();

            // Require at least 100MB buffer + file size
            long requiredSpace = requiredBytes + (100 * 1024 * 1024);

            if (usableSpace < requiredSpace) {
                throw new InsufficientDiskSpaceException(
                        String.format("Insufficient disk space. Required: %d MB, Available: %d MB",
                                requiredSpace / (1024 * 1024),
                                usableSpace / (1024 * 1024))
                );
            }
        } catch (IOException e) {
            log.warn("Unable to check disk space: {}", e.getMessage());
            // Don't fail if we can't check - let the actual write operation fail if needed
        }
    }

    /**
     * Check if there's enough disk space (public method for controller)
     * Calls private checkDiskSpace and returns boolean instead of throwing
     * No exception means space is available
     */
    public boolean checkDiskSpaceAvailable(long requiredBytes) {
        try {
            Path uploadDir = Paths.get(config.getUploadDirectory());
            checkDiskSpace(uploadDir, requiredBytes);
            return true;
        } catch (InsufficientDiskSpaceException e) {
            log.warn("Disk space check failed: {}", e.getMessage());
            return false;
        }
    }

}