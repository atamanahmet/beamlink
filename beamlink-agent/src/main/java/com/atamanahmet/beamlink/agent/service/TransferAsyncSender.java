package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.http.HttpSender;
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
    private static final long RETRY_DELAY_MS = 2000;

    private final FileTransferRepository transferRepository;
    private final ObjectMapper objectMapper;
    private final HttpSender httpSender;

    @Async
    public void sendAsync(UUID transferId, String targetIp, int targetPort, String targetToken) {
        doSend(transferId, targetIp, targetPort, targetToken);
    }

    public CompletableFuture<Void> sendBlocking(UUID transferId, String targetIp, int targetPort, String targetToken) {
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

                // retry loop per chunk
                ChunkAckResponse ack = sendChunkWithRetry(
                        baseUrl, transferId,
                        offset, chunkEnd, transfer.getFileSize(),
                        chunk, targetToken, transfer.getMaxRetries()
                );

                if (ack.getConfirmedOffset() < offset) {
                    log.warn("Receiver offset mismatch. Rewinding from {} to {}", offset, ack.getConfirmedOffset());
                    offset = ack.getConfirmedOffset();
                    raf.seek(offset);
                    continue;
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
            if (transfer != null) {
                markFailed(transfer, e.getMessage());
            }
        }
    }

    private ChunkAckResponse sendChunkWithRetry(
            String baseUrl, UUID transferId,
            long offset, long chunkEnd, long fileSize,
            byte[] chunk, String targetToken, int maxRetries
    ) throws IOException, InterruptedException {

        Exception lastException = null;
        int stallCount = 0;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                ChunkAckResponse ack = sendChunk(baseUrl, transferId, offset, chunkEnd, fileSize, chunk, targetToken);


                if (ack.getConfirmedOffset() == offset) {
                    stallCount++;
                    lastException = new IOException("No forward progress at offset " + offset);
                    log.warn("Stall detected (attempt {}/{}): offset still at {}", attempt, maxRetries, offset);
                    if (attempt < maxRetries) {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    }
                    continue;  // ADD
                }

                return ack;
            } catch (Exception e) {
                lastException = e;
                log.warn("Chunk send failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }
            }
        }

        throw new FileTransferException(
                "Chunk failed after " + maxRetries + " attempts at offset " + offset,
                lastException
        );
    }

    private ChunkAckResponse sendChunk(
            String baseUrl, UUID transferId,
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

        HttpResponse<String> response = httpSender.send(request);

        if (response.statusCode() != 200) {
            throw new FileTransferException(
                    "Chunk rejected. Status: " + response.statusCode()
                            + " Body: " + response.body(), null
            );
        }

        return objectMapper.readValue(response.body(), ChunkAckResponse.class);
    }

    private void markFailed(FileTransfer transfer, String reason) {
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(reason);
        transferRepository.save(transfer);
    }
}