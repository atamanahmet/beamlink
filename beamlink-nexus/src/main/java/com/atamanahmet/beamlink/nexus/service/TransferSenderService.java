package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.InitiateTransferRequest;
import com.atamanahmet.beamlink.nexus.dto.InitiateTransferResponse;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.http.TransferHttpClient;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.util.PathNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSenderService {

    private static final int CHUNK_SIZE = 8388608;

    private final FileTransferRepository transferRepository;
    private final NexusConfig nexusConfig;
    private final ObjectMapper objectMapper;
    private final TransferSender asyncSender;
    private final TransferHttpClient transferHttpClient;
    private final AgentTokenService agentTokenService;
    private final NexusService nexusService;

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
        String fileName = filePath.getFileName().toString();

        FileTransfer transfer = FileTransfer.initiate(
                transferId,
                nexusService.getNexusId(),
                request.getTargetAgentId(),
                fileName,
                cleanedPath,
                fileSize
        );
        transfer.setTargetIp(request.getTargetIp());
        transfer.setTargetPort(request.getTargetPort());
        transfer.setStatus(TransferStatus.PENDING);
        transfer.setExpiresAt(Instant.now().plusSeconds(nexusConfig.getTransferExpiryHours() * 3600L));
        transferRepository.save(transfer);

        try {
            transferHttpClient.registerTransfer(request, transferId, fileName, fileSize);
        } catch (FileTransferException e) {
            transferRepository.delete(transfer);
            throw e;
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);
        log.info("Transfer initiated: {} → {} ({})", fileName, request.getTargetAgentId(), transferId);

        return new InitiateTransferResponse(transferId);
    }

    public void resume(UUID transferId) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));

        if (transfer.getStatus() != TransferStatus.PAUSED) {
            throw new FileTransferException("Transfer is not paused, current status: " + transfer.getStatus(), null);
        }

        if (transfer.getTargetIp() == null || transfer.getTargetIp().isBlank()) {
            throw new FileTransferException("Transfer has no target IP stored, cannot resume", null);
        }

        String cleanedPath = PathNormalizer.normalize(transfer.getFilePath());
        if (!Files.exists(Paths.get(cleanedPath))) {
            throw new FileTransferException("Source file no longer exists: " + cleanedPath, null);
        }

        long targetOffset = transferHttpClient.queryConfirmedOffset(
                transfer.getTargetIp(), transfer.getTargetPort(), transferId);

        if (targetOffset != transfer.getConfirmedOffset()) {
            log.info("Correcting offset for {} from {} to {} (target state)",
                    transferId, transfer.getConfirmedOffset(), targetOffset);
            transfer.setConfirmedOffset(targetOffset);
        }

        transfer.setStatus(TransferStatus.ACTIVE);
        transferRepository.save(transfer);
    }

    public FileTransfer getTransfer(UUID transferId) {
        return transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
    }

    public TransferStatus getStatus(UUID transferId) {
        return transferRepository.findStatusByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException("Transfer not found: " + transferId, null));
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
                log.info("Transfer cancelled: {}", transferId);
            }
        });
    }

    @Transactional
    public void delete(UUID transferId) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            TransferStatus s = transfer.getStatus();
            if (s == TransferStatus.COMPLETED
                    || s == TransferStatus.FAILED
                    || s == TransferStatus.CANCELLED
                    || s == TransferStatus.EXPIRED) {
                transferRepository.delete(transfer);
                log.info("Transfer deleted by user: {}", transferId);
            }
        });
    }

    @Transactional
    public void markFailed(UUID transferId, String reason) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(reason);
            transferRepository.save(transfer);
        });
    }
}