package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.agent.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.util.PathNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransferSenderService {

    private static final Logger log = LoggerFactory.getLogger(TransferSenderService.class);
    private static final int CHUNK_SIZE = 8 * 1024 * 1024;

    private final FileTransferRepository transferRepository;
    private final AgentConfig agentConfig;
    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final TransferAsyncSender asyncSender;

    /**
     * Called by TransferController when UI initiates a transfer.
     * Validates the file, registers the transfer on the target,
     * saves local FileTransfer record, then starts async sending.
     */
    public InitiateTransferResponse initiate(InitiateTransferRequest request) {

        String cleanedPath = PathNormalizer.normalize(request.getFilePath());
        Path filePath = Paths.get(cleanedPath);

        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileTransferException("File not found: " + cleanedPath, null);
        }

        long fileSize;
        try {
            fileSize = Files.size(filePath);
        } catch (IOException e) {
            throw new FileTransferException("Cannot read file size: " + cleanedPath, e);
        }

        UUID transferId = UUID.randomUUID();
        UUID sourceAgentId = agentService.getAgentId();

        // Save PENDING first
        FileTransfer transfer = FileTransfer.initiate(
                transferId,
                sourceAgentId,
                request.getTargetAgentId(),
                filePath.getFileName().toString(),
                cleanedPath,
                fileSize
        );
        transfer.setTargetIp(request.getTargetIp());
        transfer.setTargetPort(request.getTargetPort());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setExpiresAt(Instant.now().plusSeconds(agentConfig.getTransferExpiryHours() * 3600L));
        transferRepository.save(transfer);

        registerOnTarget(request, transferId, filePath.getFileName().toString(), fileSize);

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);

        asyncSender.sendAsync(transferId, request.getTargetIp(), request.getTargetPort(), request.getTargetToken());

        log.info("Transfer initiated: {} → {} ({})", filePath.getFileName(),
                request.getTargetAgentId(), transferId);

        return new InitiateTransferResponse(transferId);
    }

    @Transactional
    public void resume(UUID transferId) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.PAUSED) {
            throw new FileTransferException(
                    "Transfer is not paused, current status: " + transfer.getStatus(), null);
        }

        if (transfer.getTargetIp() == null || transfer.getTargetIp().isBlank()) {
            throw new FileTransferException(
                    "Transfer has no target IP stored, cannot resume", null);
        }

        String cleanedPath = PathNormalizer.normalize(transfer.getFilePath());
        if (!Files.exists(Paths.get(cleanedPath))) {
            throw new FileTransferException(
                    "Source file no longer exists: " + cleanedPath, null);
        }

        long targetOffset = queryTargetOffset(transfer);

        if (targetOffset != transfer.getConfirmedOffset()) {
            log.info("Correcting offset for {} from {} to {} (target state)",
                    transferId, transfer.getConfirmedOffset(), targetOffset);
            transfer.setConfirmedOffset(targetOffset);
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);

        //TODO: agent to agent auth
        asyncSender.sendAsync(transferId,
                transfer.getTargetIp(),
                transfer.getTargetPort(),
                null);
    }

    private long queryTargetOffset(FileTransfer transfer) {
        HttpClient httpClient = HttpClient.newHttpClient();
        String url = "http://" + transfer.getTargetIp() + ":"
                + transfer.getTargetPort()
                + "/api/transfers/" + transfer.getTransferId() + "/offset";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target returned " + response.statusCode()
                                + " when querying offset", null);
            }

            Map<String, Long> body = objectMapper.readValue(
                    response.body(),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});

            return body.get("confirmedOffset");

        } catch (IOException | InterruptedException e) {
            // Target is offline, transfer stays PAUSED
            throw new FileTransferException(
                    "Cannot reach target to query offset: " + url, e);
        }
    }

    /**
     * Registers the transfer on the target agent before sending any chunks.
     */
    private void registerOnTarget(
            InitiateTransferRequest request,
            UUID transferId,
            String fileName,
            long fileSize
    ) throws FileTransferException {
        HttpClient httpClient = HttpClient.newHttpClient();

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "transferId", transferId.toString(),
                    "sourceAgentId", agentService.getAgentId().toString(),
                    "fileName", fileName,
                    "fileSize", fileSize
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + request.getTargetIp() + ":"
                            + request.getTargetPort() + "/api/transfers/receive"))
                    .header("Content-Type", "application/json")
                    .header("X-Auth-Token", request.getTargetToken() != null ? request.getTargetToken() : "")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new FileTransferException(
                        "Target rejected transfer registration. Status: "
                                + response.statusCode(), null
                );
            }

        } catch (IOException | InterruptedException e) {
            throw new FileTransferException("Cannot reach target agent", e);
        }
    }

    public TransferStatus getStatus(UUID transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null))
                .getStatus();

    }public FileTransfer getTransfer(UUID transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null));
    }

    public List<FileTransfer> getAll() {
        return transferRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void cancel(UUID transferId) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            if (transfer.getStatus() == TransferStatus.ACTIVE
                    || transfer.getStatus() == TransferStatus.PAUSED) {
                transfer.setStatus(TransferStatus.CANCELLED);
                transferRepository.save(transfer);
                log.info("Transfer cancelled by user: {}", transferId);
            }
        });
    }

    /**
     * Marks transfer as failed with reason.
     */
    @Transactional
    public void markFailed(UUID transferId, String reason) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(reason);
            transferRepository.save(transfer);
        });
    }
}