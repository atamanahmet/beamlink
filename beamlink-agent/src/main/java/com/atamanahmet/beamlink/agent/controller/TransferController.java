package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.dto.*;
import com.atamanahmet.beamlink.agent.exception.FileTransferException;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import com.atamanahmet.beamlink.agent.service.BatchSenderService;
import com.atamanahmet.beamlink.agent.service.ChunkReceiverService;
import com.atamanahmet.beamlink.agent.service.DirectorySenderService;
import com.atamanahmet.beamlink.agent.service.TransferSenderService;
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
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransferSenderService senderService;
    private final ChunkReceiverService receiverService;
    private final DirectorySenderService directorySenderService;
    private final BatchSenderService batchSenderService;

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

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(toResponse(senderService.getTransfer(transferId)));
    }

    @GetMapping
    public ResponseEntity<List<TransferStatusResponse>> getAll() {

        List<TransferStatusResponse> transfers = senderService
                .getAll()
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

        senderService.cancel(transferId);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
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
     * Target receives directory registration,
     * creates records and allocates all partial files
     */
    @PostMapping("/receive-directory")
    public ResponseEntity<Void> prepareReceiveDirectory(
            @RequestBody ReceiveDirectoryRequest request) {

        receiverService.prepareReceiveDirectory(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /** Target receives batch registration,
     * creates records and allocates all partial files
     */
    @PostMapping("/receive-batch")
    public ResponseEntity<Void> prepareReceiveBatch(
            @RequestBody ReceiveBatchRequest request) {

        receiverService.prepareReceiveBatch(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Source queries this to find confirmed offset before resuming.
     */
    @GetMapping("/{transferId}/offset")
    public ResponseEntity<Map<String, Long>> getOffset(@PathVariable UUID transferId) {

        FileTransfer transfer = senderService.getTransfer(transferId);

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

    /**
     * Initiate a full directory transfer, walks source dir,
     * registers on target, sends async
     */
    @PostMapping("/directory")
    public ResponseEntity<InitiateDirectoryTransferResponse> initiateDirectory(
            @RequestBody InitiateDirectoryTransferRequest request) {

        InitiateDirectoryTransferResponse response = directorySenderService.initiate(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Initiate a batch of loose files, no directory structure, no relativePath
     */
    @PostMapping("/multi")
    public ResponseEntity<InitiateBatchTransferResponse> initiateBatch(
            @RequestBody InitiateBatchTransferRequest request) {

        InitiateBatchTransferResponse response = batchSenderService.initiate(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
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