package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupTransferAsyncSender {

    private final TransferSender transferSender;

    /**
     * Sequential send loop for any group transfer type.
     * Paused files go first, then active/pending in order.
     * Blocks per file so bandwidth is not split.
     */
    @Async
    public void sendAsync(GroupTransferContext ctx) {
        UUID groupId = ctx.groupId();
        GroupTransferService groupService = ctx.groupTransferService();

        List<FileTransfer> queue = groupService.getOrderedQueue(groupId);

        for (FileTransfer ft : queue) {
            GroupTransferStatus currentStatus = groupService.getStatus(groupId);

            if (currentStatus == GroupTransferStatus.CANCELLED
                    || currentStatus == GroupTransferStatus.FAILED) {
                log.info("Group transfer {} stopped before file {}: status={}",
                        groupId, ft.getFileName(), currentStatus);
                return;
            }

            if (currentStatus == GroupTransferStatus.PAUSED) {
                log.info("Group transfer {} paused before file: {}",
                        groupId, ft.getFileName());
                return;
            }

            try {
                transferSender.sendBlocking(
                        ft.getTransferId(), ctx.targetIp(), ctx.targetPort());
            } catch (RuntimeException e) {
                log.error("File failed in group {}: {}", groupId, ft.getFileName(), e);
            }
        }

        resolveGroupOutcome(groupId, groupService);
    }

    private void resolveGroupOutcome(UUID groupId, GroupTransferService groupService) {
        List<FileTransfer> children = groupService.getAllChildren(groupId);

        long total = children.size();
        long completed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.COMPLETED)
                .count();
        long cancelled = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.CANCELLED)
                .count();
        long failed = children.stream()
                .filter(ft -> ft.getStatus() == TransferStatus.FAILED)
                .count();

        if (cancelled > 0) {
            groupService.markCancelled(groupId);
            log.info("Group transfer cancelled: {}", groupId);
        } else if (completed == total) {
            groupService.markCompleted(groupId);
            log.info("Group transfer completed: {}", groupId);
        } else if (completed > 0 && completed + failed == total) {
            groupService.markPartial(groupId);
            log.info("Group transfer partial: {}/{} completed", completed, total);
        } else {
            groupService.markFailed(groupId);
            log.warn("Group transfer failed: {}", groupId);
        }
    }
}