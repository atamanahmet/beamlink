package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateDirectoryTransferResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveDirectoryRequest;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DirectorySenderService {

    private static final Logger log = LoggerFactory.getLogger(DirectorySenderService.class);

    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final AgentService agentService;
    private final AgentConfig agentConfig;
    private final TransferAsyncSender asyncSender;
    private final ObjectMapper objectMapper;

    /**
     * Called by controller. Validates and walks the directory fully,
     * saves all records, registers on target, then fires async send.
     * Returns directoryTransferId immediately.
     */
    public InitiateDirectoryTransferResponse initiate(InitiateDirectoryTransferRequest request) {

        String cleanedPath = PathNormalizer.normalize(request.getSourcePath());
        Path sourceDir = Paths.get(cleanedPath);

        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new FileTransferException("Directory not found: " + cleanedPath, null);
        }

        // Full walk, must complete before any network call
        WalkResult walk = walkDirectory(sourceDir);

        UUID directoryTransferId = UUID.randomUUID();
        UUID sourceAgentId = agentService.getAgentId();
        String directoryName = sourceDir.getFileName().toString();

        DirectoryTransfer directoryTransfer = DirectoryTransfer.initiate(
                directoryTransferId,
                sourceAgentId,
                request.getTargetAgentId(),
                request.getTargetIp(),
                request.getTargetPort(),
                directoryName,
                cleanedPath,
                walk.files.size(),
                walk.totalSize,
                walk.emptyDirectories
        );
        directoryTransferRepository.save(directoryTransfer);

        // Build FileTransfer records and registration payload together
        List<FileTransfer> fileTransfers = new ArrayList<>();
        List<ReceiveDirectoryRequest.FileEntry> fileEntries = new ArrayList<>();

        for (WalkResult.FileEntry entry : walk.files) {
            UUID transferId = UUID.randomUUID();
            String fileName = entry.absolutePath.getFileName().toString();
            String relativePath = sourceDir.relativize(entry.absolutePath).toString();

            FileTransfer ft = FileTransfer.initiate(
                    transferId,
                    sourceAgentId,
                    request.getTargetAgentId(),
                    fileName,
                    entry.absolutePath.toString(),
                    entry.fileSize
            );
            ft.setDirectoryTransferId(directoryTransferId);
            ft.setRelativePath(relativePath);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveDirectoryRequest.FileEntry fe = new ReceiveDirectoryRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(fileName);
            fe.setRelativePath(relativePath);
            fe.setFileSize(entry.fileSize);
            fileEntries.add(fe);
        }

        fileTransferRepository.saveAll(fileTransfers);

        // Register everything on target in one call, walk is complete, no partial state
        registerOnTarget(request, directoryTransferId, sourceAgentId, directoryName,
                walk, fileEntries);

        // Target accepted, mark active and start
        directoryTransfer.setStatus(GroupTransferStatus.ACTIVE);
        directoryTransferRepository.save(directoryTransfer);

        sendDirectoryAsync(directoryTransferId, request.getTargetIp(),
                request.getTargetPort(), request.getTargetToken());

        log.info("Directory transfer initiated: {} → {} ({})",
                directoryName, request.getTargetAgentId(), directoryTransferId);

        return new InitiateDirectoryTransferResponse(directoryTransferId);
    }

    /**
     * Resumes a PAUSED directory transfer.
     * PAUSED file resumes from confirmed offset, remaining PENDING files continue in order.
     */
    public void resume(UUID directoryTransferId) {
        DirectoryTransfer dt = directoryTransferRepository.findById(directoryTransferId)
                .orElseThrow(() -> new FileTransferException(
                        "Directory transfer not found: " + directoryTransferId, null));

        if (dt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Directory transfer is not paused, current status: " + dt.getStatus(), null);
        }

        dt.setStatus(GroupTransferStatus.ACTIVE);
        directoryTransferRepository.save(dt);

        sendDirectoryAsync(directoryTransferId, dt.getTargetIp(),
                dt.getTargetPort(), null); // token not stored — TODO: agent-to-agent auth
    }

    @Async
    public void sendDirectoryAsync(UUID directoryTransferId, String targetIp,
                                   int targetPort, String targetToken) {
        List<FileTransfer> children = fileTransferRepository
                .findByDirectoryTransferId(directoryTransferId);

        // Order: PAUSED first (resume in progress), then PENDING in creation order
        List<FileTransfer> queue = new ArrayList<>();
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PAUSED)
                .forEach(queue::add);
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PENDING
                        || ft.getStatus() == TransferStatus.ACTIVE)
                .forEach(queue::add);

        for (FileTransfer ft : queue) {
            // Check group status before each file
            DirectoryTransfer dt = directoryTransferRepository
                    .findById(directoryTransferId).orElse(null);
            if (dt == null || dt.getStatus() == GroupTransferStatus.CANCELLED
                    || dt.getStatus() == GroupTransferStatus.FAILED) {
                log.info("Directory transfer stopped before file {}: group status={}",
                        ft.getFileName(), dt != null ? dt.getStatus() : "gone");
                return;
            }
            if (dt.getStatus() == GroupTransferStatus.PAUSED) {
                log.info("Directory transfer paused, stopping before file: {}", ft.getFileName());
                return;
            }

            try {
                asyncSender.sendAsyncFuture(
                        ft.getTransferId(), targetIp, targetPort, targetToken
                ).get();
            } catch (Exception e) {
                log.error("File failed in directory transfer {}: {}",
                        directoryTransferId, ft.getFileName(), e);
                markFileTransferFailed(ft, e.getMessage());
            }
        }

        // All files processed — determine final group status
        completeDirectoryTransfer(directoryTransferId);
    }

    private void completeDirectoryTransfer(UUID directoryTransferId) {
        List<FileTransfer> children = fileTransferRepository
                .findByDirectoryTransferId(directoryTransferId);

        long completed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
        long failed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.FAILED
                        || ft.getStatus() == TransferStatus.CANCELLED).count();

        DirectoryTransfer dt = directoryTransferRepository
                .findById(directoryTransferId).orElse(null);
        if (dt == null) return;

        if (failed == 0 && completed == children.size()) {
            dt.setStatus(GroupTransferStatus.COMPLETED);
            dt.setCompletedAt(Instant.now());
            log.info("Directory transfer completed: {}", directoryTransferId);
        } else if (completed > 0 && failed > 0) {
            dt.setStatus(GroupTransferStatus.PARTIAL);
            log.info("Directory transfer partial: {}/{} completed, {}/{} failed",
                    completed, children.size(), failed, children.size());
        } else {
            dt.setStatus(GroupTransferStatus.FAILED);
            log.warn("Directory transfer failed: {}", directoryTransferId);
        }

        directoryTransferRepository.save(dt);
    }

    private void markFileTransferFailed(FileTransfer ft, String reason) {
        ft.setStatus(TransferStatus.FAILED);
        ft.setFailureReason(reason);
        fileTransferRepository.save(ft);
    }

    /**
     * Full recursive walk. Completes entirely before any network call.
     * Throws if any file is unreadable — no partial walks ever reach the target.
     */
    private WalkResult walkDirectory(Path root) {
        List<WalkResult.FileEntry> files = new ArrayList<>();
        List<String> emptyDirectories = new ArrayList<>();
        long totalSize = 0;

        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> all = stream.sorted().toList();

            for (Path path : all) {
                if (path.equals(root)) continue;

                if (Files.isDirectory(path)) {
                    // Check if this directory has any files (not just subdirs)
                    boolean hasFiles;
                    try (Stream<Path> dirContents = Files.list(path)) {
                        hasFiles = dirContents.anyMatch(Files::isRegularFile);
                    }
                    if (!hasFiles) {
                        emptyDirectories.add(root.relativize(path).toString());
                    }
                } else if (Files.isRegularFile(path)) {
                    if (!Files.isReadable(path)) {
                        throw new FileTransferException(
                                "File is not readable: " + path, null);
                    }
                    long size = Files.size(path);
                    files.add(new WalkResult.FileEntry(path, size));
                    totalSize += size;
                }
            }
        } catch (IOException e) {
            throw new FileTransferException("Failed to walk directory", e);
        }

        if (files.isEmpty()) {
            throw new FileTransferException(
                    "Directory contains no files to transfer", null);
        }

        WalkResult result = new WalkResult();
        result.files = files;
        result.emptyDirectories = emptyDirectories.isEmpty()
                ? Collections.emptyList() : emptyDirectories;
        result.totalSize = totalSize;
        return result;
    }

    private void registerOnTarget(
            InitiateDirectoryTransferRequest request,
            UUID directoryTransferId,
            UUID sourceAgentId,
            String directoryName,
            WalkResult walk,
            List<ReceiveDirectoryRequest.FileEntry> fileEntries
    ) {
        ReceiveDirectoryRequest payload = new ReceiveDirectoryRequest();
        payload.setDirectoryTransferId(directoryTransferId);
        payload.setSourceAgentId(sourceAgentId);
        payload.setDirectoryName(directoryName);
        payload.setTotalFiles(walk.files.size());
        payload.setTotalSize(walk.totalSize);
        payload.setEmptyDirectories(walk.emptyDirectories);
        payload.setFiles(fileEntries);

        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":"
                            + request.getTargetPort() + "/api/transfers/receive-directory"))
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Token",
                            request.getTargetToken() != null ? request.getTargetToken() : "")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected directory registration. Status: "
                                + response.statusCode(), null);
            }

        } catch (IOException | InterruptedException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    private static class WalkResult {
        List<FileEntry> files;
        List<String> emptyDirectories;
        long totalSize;

        static class FileEntry {
            final Path absolutePath;
            final long fileSize;

            FileEntry(Path absolutePath, long fileSize) {
                this.absolutePath = absolutePath;
                this.fileSize = fileSize;
            }
        }
    }
}