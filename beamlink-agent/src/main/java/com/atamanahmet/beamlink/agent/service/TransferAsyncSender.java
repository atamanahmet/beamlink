package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class TransferAsyncSender {

    private static final Logger log = LoggerFactory.getLogger(TransferAsyncSender.class);
    private static final int CHUNK_SIZE = 8 * 1024 * 1024;
    private static final int MAX_CHUNK_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    private final FileTransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    @Async
    public void sendAsync(UUID transferId, String targetIp, int targetPort, String targetToken) {
        doSend(transferId, targetIp, targetPort, targetToken);
    }

    @Async
    public CompletableFuture<Void> sendAsyncFuture(UUID transferId, String targetIp, int targetPort, String targetToken) {
        doSend(transferId, targetIp, targetPort, targetToken);
        return CompletableFuture.completedFuture(null);
    }

    private void doSend(UUID transferId, String targetIp, int targetPort, String targetToken) {
        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElse(null);

        if (transfer == null) {
            log.warn("Transfer not found, aborting async send: {}", transferId);
            return;
        }

        HttpClient httpClient = HttpClient.newHttpClient();
        String baseUrl = "http://" + targetIp + ":" + targetPort;

        try (RandomAccessFile raf = new RandomAccessFile(
                Paths.get(transfer.getFilePath()).toFile(), "r")) {

            long offset = transfer.getConfirmedOffset();
            raf.seek(offset);

            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;

            while ((bytesRead = raf.read(buffer)) != -1) {

                transfer = transferRepository.findByTransferId(transferId)
                        .orElse(null);

                if (transfer == null) {
                    log.warn("Transfer disappeared during send, stopping: {}", transferId);
                    return;
                }

                if (transfer.getStatus() == TransferStatus.CANCELLED) {
                    log.info("Transfer cancelled: {}", transferId);
                    return;
                }
                if (transfer.getStatus() == TransferStatus.PAUSED) {
                    log.info("Transfer paused: {}", transferId);
                    return;
                }

                byte[] chunk = Arrays.copyOf(buffer, bytesRead);

                long chunkEnd = offset + bytesRead - 1;

                // Retry loop per chunk
                ChunkAckResponse ack = sendChunkWithRetry(
                        httpClient, baseUrl, transferId,
                        offset, chunkEnd, transfer.getFileSize(), chunk, targetToken
                );

                if (ack.getConfirmedOffset() < offset) {
                    log.warn("Receiver offset mismatch. Rewinding from {} to {}", offset, ack.getConfirmedOffset());
                    offset = ack.getConfirmedOffset();
                    raf.seek(offset);
                    continue;
                }

                if (ack.getConfirmedOffset() == offset) {
                    throw new FileTransferException("No forward progress at offset " + offset, null);
                }

                offset = ack.getConfirmedOffset();

                transfer.setConfirmedOffset(offset);
                transfer.setLastChunkAt(Instant.now());
                transferRepository.save(transfer);

                if (ack.isComplete()) {
                    transfer.setStatus(TransferStatus.COMPLETED);
                    transferRepository.save(transfer);
                    log.info("Transfer completed: {}", transferId);
                    return;
                }
            }

        } catch (Exception e) {
            log.error("Transfer failed: {}", transferId, e);
            markFailed(transferId, e.getMessage());
        }
    }

    private ChunkAckResponse sendChunkWithRetry(
            HttpClient httpClient, String baseUrl, UUID transferId,
            long offset, long chunkEnd, long fileSize,
            byte[] chunk, String targetToken
    ) throws IOException, InterruptedException {

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_CHUNK_RETRIES; attempt++) {
            try {
                return sendChunk(httpClient, baseUrl, transferId,
                        offset, chunkEnd, fileSize, chunk, targetToken);
            } catch (Exception e) {
                lastException = e;
                log.warn("Chunk send failed (attempt {}/{}): {}", attempt, MAX_CHUNK_RETRIES, e.getMessage());
                if (attempt < MAX_CHUNK_RETRIES) {
                    Thread.sleep(RETRY_DELAY_MS * attempt); // back off
                }
            }
        }

        throw new FileTransferException(
                "Chunk failed after " + MAX_CHUNK_RETRIES + " attempts at offset " + offset,
                lastException
        );
    }

    private ChunkAckResponse sendChunk(
            HttpClient httpClient, String baseUrl, UUID transferId,
            long offset, long chunkEnd, long fileSize,
            byte[] chunk, String targetToken
    ) throws IOException, InterruptedException {

        String contentRange = "bytes " + offset + "-" + chunkEnd + "/" + fileSize;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/transfers/" + transferId + "/chunk"))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Range", contentRange)
                .header("X-Auth-Token", targetToken != null ? targetToken : "")
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new FileTransferException(
                    "Chunk rejected. Status: " + response.statusCode()
                            + " Body: " + response.body(), null
            );
        }

        return objectMapper.readValue(response.body(), ChunkAckResponse.class);
    }

    private void markFailed(UUID transferId, String reason) {
        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(reason);
            transferRepository.save(transfer);
        });
    }
}