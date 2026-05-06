package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import com.atamanahmet.beamlink.agent.repository.DirectoryTransferRepository;
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
public class DirectoryAsyncSender {

    private static final Logger log = LoggerFactory.getLogger(DirectoryAsyncSender.class);

    private final DirectoryTransferRepository directoryTransferRepository;
    private final FileTransferRepository fileTransferRepository;
    private final TransferAsyncSender transferAsyncSender;

    /**
     * Called by DirectorySenderService
     * Blocks until current file completes, sequential by design
     * Single file uses full bandwidth before moving to the next file.
     */
    @Async
    public void sendAsync(UUID directoryTransferId, String targetIp,
                          int targetPort, String targetToken) {

        List<FileTransfer> children = fileTransferRepository
                .findByDirectoryTransferId(directoryTransferId);


        List<FileTransfer> queue = new ArrayList<>();
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PAUSED)
                .forEach(queue::add);
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.ACTIVE)
                .forEach(queue::add);
        children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.PENDING)
                .forEach(queue::add);

        DirectoryTransfer dt = directoryTransferRepository.findById(directoryTransferId).orElse(null);

        if (dt == null) return;

        for (FileTransfer ft : queue) {

            if (dt.getStatus() == GroupTransferStatus.CANCELLED
                    || dt.getStatus() == GroupTransferStatus.FAILED) {
                log.info("Directory transfer stopped before file {}: group status={}",
                        ft.getFileName(), dt.getStatus());
                return;
            }
            if (dt.getStatus() == GroupTransferStatus.PAUSED) {
                log.info("Directory transfer paused, stopping before file: {}", ft.getFileName());
                return;
            }

            try {
                transferAsyncSender.sendBlocking(
                        ft.getTransferId(), targetIp, targetPort, targetToken
                );
            }
            catch (RuntimeException e) {
                log.error("File failed in directory {}: {}", directoryTransferId, ft.getFileName(), e);
            }

            dt = directoryTransferRepository.findById(directoryTransferId).orElse(null);
            if (dt == null) return;
        }

        completeDirectoryTransfer(directoryTransferId);
    }

    private void completeDirectoryTransfer(UUID directoryTransferId) {

        List<FileTransfer> updated = fileTransferRepository.findByDirectoryTransferId(directoryTransferId);

        long completed = updated.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.COMPLETED).count();
        long failed = updated.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.FAILED
                        || ft.getStatus() == TransferStatus.CANCELLED).count();

        DirectoryTransfer dt = directoryTransferRepository
                .findById(directoryTransferId).orElse(null);
        if (dt == null) return;

        long terminal = completed + failed;

        if (failed == 0 && completed == updated.size()) {
            dt.setStatus(GroupTransferStatus.COMPLETED);
            dt.setCompletedAt(Instant.now());
            log.info("Directory transfer completed: {}", directoryTransferId);
        } else if (terminal == updated.size() && completed > 0 && failed > 0) {
            dt.setStatus(GroupTransferStatus.PARTIAL);
            log.info("Directory transfer partial: {}/{} completed, {}/{} failed",
                    completed, updated.size(), failed, updated.size());
        } else {
            dt.setStatus(GroupTransferStatus.FAILED);
            log.warn("Directory transfer failed: {}", directoryTransferId);
        }

        directoryTransferRepository.save(dt);
    }
}