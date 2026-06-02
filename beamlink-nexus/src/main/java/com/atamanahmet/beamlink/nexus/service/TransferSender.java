package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.SettingKey;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.ChunkAckResponse;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.http.HttpSender;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.constants.TransferConstants;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferSender {

    private final FileTransferRepository transferRepository;
    private final ObjectMapper objectMapper;
    private final HttpSender httpSender;
    private final SettingsService settingsService;

    private final ConcurrentHashMap<UUID, Object> transferLocks = new ConcurrentHashMap<>();

    @Async
    public void sendAsync(UUID transferId, String targetIp, int targetPort) {
        doSend(transferId, targetIp, targetPort);
    }

    public CompletableFuture<Void> sendBlocking(UUID transferId, String targetIp, int targetPort) {
        doSend(transferId, targetIp, targetPort);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Entry point. Opens file, runs sliding window dispatch, cleans up.
     * Tracks highest confirmed offset seen across all chunks.
     * Prevents regression on out-of-order completions.
     */
    private void doSend(UUID transferId, String targetIp, int targetPort) {
        log.info("doSend started transferId: {}", transferId);

        FileTransfer transfer = transferRepository.findByTransferId(transferId).orElse(null);
        if (transfer == null) {
            log.warn("Transfer not found, aborting: {}", transferId);
            return;
        }

        ExecutorService windowExecutor = Executors.newFixedThreadPool(TransferConstants.WINDOW_SIZE);
        Semaphore window = new Semaphore(TransferConstants.WINDOW_SIZE);
        ConcurrentLinkedQueue<Exception> errors = new ConcurrentLinkedQueue<>();

        AtomicLong maxConfirmed = new AtomicLong(transfer.getConfirmedOffset());

        String baseUrl = "http://" + targetIp + ":" + targetPort;

        try (RandomAccessFile raf = new RandomAccessFile(
                Paths.get(transfer.getFilePath()).toFile(), "r")) {

            readAndDispatchChunks(raf, transfer, baseUrl, window, windowExecutor, errors, maxConfirmed);
            drainWindow(window, errors);

        } catch (Exception e) {
            log.error("Transfer failed: {}", transferId, e);
            FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
            if (current != null) markFailed(current, e.getMessage());
        } finally {
            windowExecutor.shutdown();
        }
    }

    /**
     * Reads file chunk by chunk, checks pause/cancel each iteration,
     * submits each chunk to window executor, applies speed cap delay.
     */
    private void readAndDispatchChunks(
            RandomAccessFile raf,
            FileTransfer transfer,
            String baseUrl,
            Semaphore window,
            ExecutorService windowExecutor,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed
    ) throws Exception {

        UUID transferId = transfer.getTransferId();
        long offset = transfer.getConfirmedOffset();
        raf.seek(offset);
        byte[] buffer = new byte[TransferConstants.CHUNK_SIZE];
        int bytesRead;

        while ((bytesRead = raf.read(buffer)) != -1) {

            FileTransfer current = transferRepository.findByTransferId(transferId).orElse(null);
            if (current == null) {
                log.warn("Transfer disappeared during send: {}", transferId);
                break;
            }
            if (current.getStatus() == TransferStatus.CANCELLED) {
                log.info("Transfer cancelled: {}", transferId);
                break;
            }
            if (current.getStatus() == TransferStatus.PAUSED) {
                log.info("Transfer paused: {}", transferId);
                break;
            }
            if (!errors.isEmpty()) throw errors.poll();

            byte[] chunk = Arrays.copyOf(buffer, bytesRead);
            long chunkOffset = offset;
            long chunkEnd = offset + bytesRead - 1L;
            long expectedMs = expectedChunkMs(bytesRead);

            window.acquire();
            long dispatchStart = System.currentTimeMillis();
            windowExecutor.submit(() -> dispatchChunk(
                    current, baseUrl, chunkOffset, chunkEnd, chunk, window, errors, maxConfirmed
            ));

            applySpeedCap(expectedMs, dispatchStart);
            offset += bytesRead;
        }
    }

    /**
     * Sends one chunk with retry, updates progress on success, marks complete if last chunk.
     */
    private void dispatchChunk(
            FileTransfer transfer,
            String baseUrl,
            long chunkOffset,
            long chunkEnd,
            byte[] chunk,
            Semaphore window,
            ConcurrentLinkedQueue<Exception> errors,
            AtomicLong maxConfirmed
    ) {
        try {
            ChunkAckResponse ack = sendChunkWithRetry(
                    baseUrl, transfer.getTransferId(),
                    chunkOffset, chunkEnd,
                    transfer.getFileSize(), chunk,
                    transfer.getMaxRetries()
            );

            updateTransferProgress(transfer, ack.getConfirmedOffset(), maxConfirmed);

            if (ack.isComplete()) {
                transfer.setStatus(TransferStatus.COMPLETED);
                transferRepository.save(transfer);
                transferLocks.remove(transfer.getTransferId());
                log.info("Transfer completed: {}", transfer.getTransferId());
            }
        } catch (Exception e) {
            errors.add(e);
        } finally {
            window.release();
        }
    }

    /**
     * Blocks until all window slots free, means all in-flight chunks finished.
     * Then checks if any chunk failed.
     */
    private void drainWindow(
            Semaphore window,
            ConcurrentLinkedQueue<Exception> errors
    ) throws Exception {
        window.acquire(TransferConstants.WINDOW_SIZE);
        if (!errors.isEmpty()) throw errors.poll();
    }

    /**
     * Sleeps remaining time if chunk sent faster than speed cap allows.
     */
    private void applySpeedCap(long expectedMs, long dispatchStart) throws InterruptedException {
        long elapsed = System.currentTimeMillis() - dispatchStart;
        long sleepMs = expectedMs - elapsed;
        if (sleepMs > 0) Thread.sleep(sleepMs);
    }

    /**
     * How long this chunk should take at current cap. Returns millis.
     * chunkBytes / (capMbps * 1_000_000 / 8) = seconds, * 1000 = ms
     */
    private long expectedChunkMs(int chunkBytes) {
        double capBytesPerSec = settingsService.getDouble(
                SettingKey.TRANSFER_SPEED_CAP_MBPS, 80.0) * 1_000_000.0 / 8.0;
        return (long) ((chunkBytes / capBytesPerSec) * 1000);
    }

    /**
     * Only saves if newOffset beats current max. Prevents out-of-order chunk regression.
     */
    private void updateTransferProgress(
            FileTransfer transfer, long newOffset, AtomicLong maxConfirmed) {

        long prev;
        do {
            prev = maxConfirmed.get();
            if (newOffset <= prev) return;
        } while (!maxConfirmed.compareAndSet(prev, newOffset));

        Object lock = transferLocks.computeIfAbsent(transfer.getTransferId(), id -> new Object());
        synchronized (lock) {
            Instant now = Instant.now();
            if (transfer.getLastChunkAt() != null) {
                long delta = now.toEpochMilli() - transfer.getLastChunkAt().toEpochMilli();
                transfer.setActiveTransferMs(transfer.getActiveTransferMs() + delta);
            }
            transfer.setConfirmedOffset(newOffset);
            transfer.setLastChunkAt(now);
            transferRepository.save(transfer);
        }
    }

    private ChunkAckResponse sendChunkWithRetry(
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk, int maxRetries
    ) throws IOException, InterruptedException {

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return sendChunk(baseUrl, transferId, offset, chunkEnd, fileSize, chunk);
            } catch (Exception e) {
                lastException = e;
                log.warn("Chunk send failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                if (attempt < maxRetries) Thread.sleep(TransferConstants.RETRY_DELAY_MS * attempt);
            }
        }

        throw new FileTransferException(
                "Chunk failed after " + maxRetries + " attempts at offset " + offset, lastException);
    }

    private ChunkAckResponse sendChunk(
            String baseUrl, UUID transferId, long offset, long chunkEnd,
            long fileSize, byte[] chunk
    ) throws IOException, InterruptedException {

        String contentRange = "bytes " + offset + "-" + chunkEnd + "/" + fileSize;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/transfers/" + transferId + "/chunk"))
                .header("Content-Type", "application/octet-stream")
                .header("Content-Range", contentRange)
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(chunk))
                .build();

        HttpResponse<String> response = httpSender.send(request);
        if (response.statusCode() != 200) {
            throw new FileTransferException(
                    "Chunk rejected. Status: " + response.statusCode() +
                            " Body: " + response.body(), null);
        }

        return objectMapper.readValue(response.body(), ChunkAckResponse.class);
    }

    private void markFailed(FileTransfer transfer, String reason) {
        transfer.setStatus(TransferStatus.FAILED);
        transfer.setFailureReason(reason);
        transferRepository.save(transfer);
        transferLocks.remove(transfer.getTransferId());
    }
}