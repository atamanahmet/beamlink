package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateBatchTransferResponse;
import com.atamanahmet.beamlink.agent.dto.ReceiveBatchRequest;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchSenderService {

    private static final Logger log = LoggerFactory.getLogger(BatchSenderService.class);

    private final BatchTransferRepository batchTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final AgentService agentService;
    private final AgentConfig agentConfig;
    private final TransferAsyncSender asyncSender;
    private final ObjectMapper objectMapper;

    public InitiateBatchTransferResponse initiate(InitiateBatchTransferRequest request) {

        if (request.getFilePaths() == null || request.getFilePaths().isEmpty()) {
            throw new FileTransferException("No file paths provided for batch transfer", null);
        }

        // Validate and measure all files before touching the network
        List<ValidatedFile> validatedFiles = validateFiles(request.getFilePaths());

        UUID batchTransferId = UUID.randomUUID();
        UUID sourceAgentId = agentService.getAgentId();

        long totalSize = validatedFiles.stream().mapToLong(vf -> vf.fileSize).sum();

        BatchTransfer batchTransfer = BatchTransfer.initiate(
                batchTransferId,
                sourceAgentId,
                request.getTargetAgentId(),
                request.getTargetIp(),
                request.getTargetPort(),
                validatedFiles.size(),
                totalSize
        );
        batchTransferRepository.save(batchTransfer);

        List<FileTransfer> fileTransfers = new ArrayList<>();
        List<ReceiveBatchRequest.FileEntry> fileEntries = new ArrayList<>();

        for (ValidatedFile vf : validatedFiles) {
            UUID transferId = UUID.randomUUID();

            FileTransfer ft = FileTransfer.initiate(
                    transferId,
                    sourceAgentId,
                    request.getTargetAgentId(),
                    vf.path.getFileName().toString(),
                    vf.path.toString(),
                    vf.fileSize
            );
            ft.setBatchTransferId(batchTransferId);
            ft.setTargetIp(request.getTargetIp());
            ft.setTargetPort(request.getTargetPort());
            ft.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
            fileTransfers.add(ft);

            ReceiveBatchRequest.FileEntry fe = new ReceiveBatchRequest.FileEntry();
            fe.setTransferId(transferId);
            fe.setFileName(vf.path.getFileName().toString());
            fe.setFileSize(vf.fileSize);
            fileEntries.add(fe);
        }

        fileTransferRepository.saveAll(fileTransfers);

        registerOnTarget(request, batchTransferId, sourceAgentId,
                validatedFiles.size(), totalSize, fileEntries);

        batchTransfer.setStatus(GroupTransferStatus.ACTIVE);
        batchTransferRepository.save(batchTransfer);

        sendBatchAsync(batchTransferId, request.getTargetIp(),
                request.getTargetPort(), request.getTargetToken());

        log.info("Batch transfer initiated: {} files → {} ({})",
                validatedFiles.size(), request.getTargetAgentId(), batchTransferId);

        return new InitiateBatchTransferResponse(batchTransferId);
    }

    public void resume(UUID batchTransferId) {
        BatchTransfer bt = batchTransferRepository.findById(batchTransferId)
                .orElseThrow(() -> new FileTransferException(
                        "Batch transfer not found: " + batchTransferId, null));

        if (bt.getStatus() != GroupTransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Batch transfer is not paused, current status: " + bt.getStatus(), null);
        }

        bt.setStatus(GroupTransferStatus.ACTIVE);
        batchTransferRepository.save(bt);

        sendBatchAsync(batchTransferId, bt.getTargetIp(), bt.getTargetPort(), null);
    }

    @Async
    public void sendBatchAsync(UUID batchTransferId, String targetIp,
                               int targetPort, String targetToken) {
        List<FileTransfer> children = fileTransferRepository
                .findByBatchTransferId(batchTransferId);

        List<FileTransfer> queue = new ArrayList<>();
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PAUSED)
                .forEach(queue::add);
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PENDING
                        || ft.getStatus() == TransferStatus.ACTIVE)
                .forEach(queue::add);

        for (FileTransfer ft : queue) {
            BatchTransfer bt = batchTransferRepository
                    .findById(batchTransferId).orElse(null);
            if (bt == null || bt.getStatus() == GroupTransferStatus.CANCELLED
                    || bt.getStatus() == GroupTransferStatus.FAILED) {
                log.info("Batch transfer stopped before file {}: group status={}",
                        ft.getFileName(), bt != null ? bt.getStatus() : "gone");
                return;
            }
            if (bt.getStatus() == GroupTransferStatus.PAUSED) {
                log.info("Batch transfer paused, stopping before file: {}", ft.getFileName());
                return;
            }

            try {
                asyncSender.sendAsyncFuture(
                        ft.getTransferId(), targetIp, targetPort, targetToken
                ).get();
            } catch (Exception e) {
                log.error("File failed in batch transfer {}: {}",
                        batchTransferId, ft.getFileName(), e);
                markFileTransferFailed(ft, e.getMessage());
            }
        }

        completeBatchTransfer(batchTransferId);
    }

    private void completeBatchTransfer(UUID batchTransferId) {
        List<FileTransfer> children = fileTransferRepository
                .findByBatchTransferId(batchTransferId);

        long completed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
        long failed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.FAILED
                        || ft.getStatus() == TransferStatus.CANCELLED).count();

        BatchTransfer bt = batchTransferRepository.findById(batchTransferId).orElse(null);
        if (bt == null) return;

        if (failed == 0 && completed == children.size()) {
            bt.setStatus(GroupTransferStatus.COMPLETED);
            bt.setCompletedAt(Instant.now());
            log.info("Batch transfer completed: {}", batchTransferId);
        } else if (completed > 0 && failed > 0) {
            bt.setStatus(GroupTransferStatus.PARTIAL);
            log.info("Batch transfer partial: {}/{} completed",
                    completed, children.size());
        } else {
            bt.setStatus(GroupTransferStatus.FAILED);
            log.warn("Batch transfer failed: {}", batchTransferId);
        }

        batchTransferRepository.save(bt);
    }

    private List<ValidatedFile> validateFiles(List<String> filePaths) {
        List<ValidatedFile> result = new ArrayList<>();
        for (String raw : filePaths) {
            String cleaned = PathNormalizer.normalize(raw);
            Path path = Paths.get(cleaned);

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new FileTransferException("File not found: " + cleaned, null);
            }
            if (!Files.isReadable(path)) {
                throw new FileTransferException("File not readable: " + cleaned, null);
            }

            long size;
            try {
                size = Files.size(path);
            } catch (IOException e) {
                throw new FileTransferException("Cannot read file size: " + cleaned, e);
            }

            result.add(new ValidatedFile(path, size));
        }
        return result;
    }

    private void markFileTransferFailed(FileTransfer ft, String reason) {
        ft.setStatus(TransferStatus.FAILED);
        ft.setFailureReason(reason);
        fileTransferRepository.save(ft);
    }

    private void registerOnTarget(
            InitiateBatchTransferRequest request,
            UUID batchTransferId,
            UUID sourceAgentId,
            int totalFiles,
            long totalSize,
            List<ReceiveBatchRequest.FileEntry> fileEntries
    ) {
        ReceiveBatchRequest payload = new ReceiveBatchRequest();
        payload.setBatchTransferId(batchTransferId);
        payload.setSourceAgentId(sourceAgentId);
        payload.setTotalFiles(totalFiles);
        payload.setTotalSize(totalSize);
        payload.setFiles(fileEntries);

        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":"
                            + request.getTargetPort() + "/api/transfers/receive-batch"))
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Token",
                            request.getTargetToken() != null ? request.getTargetToken() : "")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected batch registration. Status: "
                                + response.statusCode(), null);
            }

        } catch (IOException | InterruptedException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    private static class ValidatedFile {
        final Path path;
        final long fileSize;

        ValidatedFile(Path path, long fileSize) {
            this.path = path;
            this.fileSize = fileSize;
        }
    }
}