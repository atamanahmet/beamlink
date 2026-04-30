package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.repository.FileTransferRepository;
import com.atamanahmet.beamlink.nexus.service.ChunkReceiverService;
import com.atamanahmet.beamlink.nexus.service.TransferSenderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/transfers")
@RequiredArgsConstructor
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferSenderService senderService;
    private final ChunkReceiverService receiverService;
    private final FileTransferRepository transferRepository;

    /**
     * User initiates a transfer from the UI.
     * Returns transferId immediately, sending happens async in background.
     */
    @PostMapping
    public ResponseEntity<InitiateTransferResponse> initiate(
            @RequestBody InitiateTransferRequest request) {

        InitiateTransferResponse response = senderService.initiate(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * UI polls this every second to show progress.
     */
    @GetMapping("/{transferId}/status")
    public ResponseEntity<TransferStatusResponse> getStatus(
            @PathVariable UUID transferId) {

        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(toResponse(transfer));
    }

    @GetMapping
    public ResponseEntity<List<TransferStatusResponse>> getAll() {
        List<TransferStatusResponse> transfers = transferRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(transfers);
    }

    /**
     * Resume a paused transfer.
     * Queries target for confirmed offset first, then restarts async sending.
     * Returns 409 if transfer is not paused.
     * Returns 503 if target is unreachable, UI keeps showing PAUSED with disabled resume button.
     */
    @PostMapping("/{transferId}/resume")
    public ResponseEntity<Void> resume(@PathVariable UUID transferId) {
        try {
            senderService.resume(transferId);
            return ResponseEntity.ok().build();
        } catch (FileTransferException e) {
            // Target offline or wrong state
            String msg = e.getMessage() != null ? e.getMessage() : "Resume failed";
            if (msg.contains("Cannot reach target")) {
                return ResponseEntity.status(503).build();
            }
            if (msg.contains("not paused")) {
                return ResponseEntity.status(409).build();
            }
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }
    }

    /**
     * Cancel an in progress transfer.
     * Sender loop checks status before each chunk and stops on CANCELLED.
     */
    @DeleteMapping("/{transferId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID transferId) {

        transferRepository.findByTransferId(transferId).ifPresent(transfer -> {
            if (transfer.getStatus() == TransferStatus.ACTIVE
                    || transfer.getStatus() == TransferStatus.PAUSED) {
                transfer.setStatus(TransferStatus.CANCELLED);
                transferRepository.save(transfer);
                log.info("Transfer cancelled by user: {}", transferId);
            }
        });

        return ResponseEntity
                .noContent()
                .build();
    }

    /**
     * Delete existing transfer
     */
    @DeleteMapping("/{transferId}/delete")
    public ResponseEntity<Void> delete(@PathVariable UUID transferId) {
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
        return ResponseEntity.noContent().build();
    }

    /**
     * Source registers the transfer on target before sending any chunks.
     * Target prepares the partial file on disk and saves its own FileTransfer record.
     */
    @PostMapping("/receive")
    public ResponseEntity<Void> prepareReceive(@RequestBody Map<String, Object> body) {

        UUID transferId = UUID.fromString((String) body.get("transferId"));
        UUID sourceAgentId = UUID.fromString((String) body.get("sourceAgentId"));
        String fileName = (String) body.get("fileName");
        long fileSize = ((Number) body.get("fileSize")).longValue();

        FileTransfer transfer = FileTransfer.initiate(
                transferId,
                sourceAgentId,
                null,
                fileName,
                null,
                fileSize
        );
        transfer.setStatus(TransferStatus.ACTIVE);

        receiverService.prepareReceive(transfer);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();

    }

    /**
     * Receives a raw chunk of bytes and writes it to disk at the correct offset.
     */
    @PatchMapping("/{transferId}/chunk")
    public ResponseEntity<ChunkAckResponse> receiveChunk(
            @PathVariable UUID transferId,
            @RequestHeader("Content-Range") String contentRange,
            HttpServletRequest request) throws IOException {

        long offset = parseOffset(contentRange);

        ChunkAckResponse ack = receiverService.receiveChunk(
                transferId,
                offset,
                request.getInputStream()   // raw stream
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ack);
    }

    /**
     * Source queries this to find confirmed offset before resuming.
     */
    @GetMapping("/{transferId}/offset")
    public ResponseEntity<Map<String, Long>> getOffset(@PathVariable UUID transferId) {

        FileTransfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new FileTransferException(
                        "Transfer not found: " + transferId, null));

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Map.of("confirmedOffset", transfer.getConfirmedOffset()));
    }

    /**
     * Parses offset from Content-Range header.
     */
    private long parseOffset(String contentRange) {
        try {
            // "bytes 0-8388607/104857600" → "0"
            String bytesPart = contentRange.replace("bytes ", "");
            String offsetStr = bytesPart.substring(0, bytesPart.indexOf('-'));
            return Long.parseLong(offsetStr.trim());
        } catch (Exception e) {
            throw new FileTransferException(
                    "Invalid Content-Range header: " + contentRange, e);
        }
    }

    // Mapper for transfer
    private TransferStatusResponse toResponse(FileTransfer t) {
        return new TransferStatusResponse(
                t.getTransferId(),
                t.getStatus(),
                t.getConfirmedOffset(),
                t.getFileSize(),
                t.getFileName(),
                t.getFailureReason(),
                t.getTargetAgentId(),
                t.getCreatedAt(),
                t.getLastChunkAt()
        );
    }
}