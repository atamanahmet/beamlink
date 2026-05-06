package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.BatchTransferRepository;
import com.atamanahmet.beamlink.agent.repository.FileTransferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BatchAsyncSender {

    private static final Logger log = LoggerFactory.getLogger(BatchAsyncSender.class);

    private final BatchTransferRepository batchTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final TransferAsyncSender transferAsyncSender;

    /**
     * Called by BatchSenderService
     * Blocks until current file completes, sequential by design
     * Single file uses full bandwidth before moving to the next file.
     */
    @Async
    public void sendAsync(UUID batchTransferId, String targetIp,
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

        // fetch once before loop, re-fetch only on status signal
        BatchTransfer bt = batchTransferRepository.findById(batchTransferId).orElse(null);
        if (bt == null) return;

        for (FileTransfer ft : queue) {

            if (bt.getStatus() == GroupTransferStatus.CANCELLED
                    || bt.getStatus() == GroupTransferStatus.FAILED) {
                log.info("Batch stopped before file {}: status={}",
                        ft.getFileName(), bt.getStatus());
                return;
            }
            if (bt.getStatus() == GroupTransferStatus.PAUSED) {
                log.info("Batch paused before file: {}", ft.getFileName());
                return;
            }

            // blocking by design, sequential, single file owns full bandwidth
            // doSend handles its own exceptions and marks the file FAILED internally
            try {
                transferAsyncSender.sendBlocking(
                        ft.getTransferId(), targetIp, targetPort, targetToken
                );
            } catch (RuntimeException e) {
                log.error("File failed in batch {}: {}", batchTransferId, ft.getFileName(), e);
            }

            bt = batchTransferRepository.findById(batchTransferId).orElse(null);
            if (bt == null) return;
        }

        completeBatchTransfer(batchTransferId, children);
    }

    private void completeBatchTransfer(UUID batchTransferId,
                                       List<FileTransfer> children) {

        List<FileTransfer> updated = fileTransferRepository
                .findByBatchTransferId(batchTransferId);

        long completed = updated.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
        long failed = updated.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.FAILED
                        || ft.getStatus() == TransferStatus.CANCELLED).count();

        BatchTransfer bt = batchTransferRepository.findById(batchTransferId).orElse(null);
        if (bt == null) return;

        long terminal = completed + failed;

        if (failed == 0 && completed == updated.size()) {
            bt.setStatus(GroupTransferStatus.COMPLETED);
            bt.setCompletedAt(Instant.now());
            log.info("Batch completed: {}", batchTransferId);
        } else if (terminal == children.size() && completed > 0 && failed > 0) {
            bt.setStatus(GroupTransferStatus.PARTIAL);
            log.info("Batch partial: {}/{} completed", completed, updated.size());
        } else {
            bt.setStatus(GroupTransferStatus.FAILED);
            log.warn("Batch failed: {}", batchTransferId);
        }

        batchTransferRepository.save(bt);
    }

}