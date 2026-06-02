package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.domain.BatchTransfer;
import com.atamanahmet.beamlink.nexus.domain.DirectoryTransfer;
import com.atamanahmet.beamlink.nexus.domain.FileTransfer;
import com.atamanahmet.beamlink.nexus.domain.enums.TransferType;
import com.atamanahmet.beamlink.nexus.dto.TransferSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TransferMapper {

    public TransferSummary fromSingle(FileTransfer ft) {
        return TransferSummary.builder()
                .id(ft.getTransferId())
                .type(TransferType.SINGLE)
                .name(ft.getFileName())
                .status(ft.getStatus().name())
                .totalSize(ft.getFileSize())
                .confirmedBytes(ft.getConfirmedOffset())
                .totalFiles(1)
                .targetAgentId(ft.getTargetAgentId())
                .targetIp(ft.getTargetIp())
                .targetPort(ft.getTargetPort())
                .createdAt(ft.getCreatedAt())
                .failureReason(ft.getFailureReason())
                .activeTransferMs(ft.getActiveTransferMs())
                .build();
    }

    /** confirmedBytes summed from children, passed in to avoid repo dependency in mapper */
    public TransferSummary fromBatch(BatchTransfer bt, long confirmedBytes, String firstName) {
        return TransferSummary.builder()
                .id(bt.getBatchTransferId())
                .dispatchId(bt.getDispatchId())
                .type(TransferType.BATCH)
                .name(firstName + " +" + (bt.getTotalFiles() - 1) + " files")
                .status(bt.getStatus().name())
                .totalSize(bt.getTotalSize())
                .confirmedBytes(confirmedBytes)
                .totalFiles(bt.getTotalFiles())
                .targetAgentId(bt.getTargetAgentId())
                .targetIp(bt.getTargetIp())
                .targetPort(bt.getTargetPort())
                .createdAt(bt.getCreatedAt())
                .completedAt(bt.getCompletedAt())
                .failureReason(bt.getFailureReason())
                .build();
    }

    /** confirmedBytes summed from children, passed in to avoid repo dependency in mapper */
    public TransferSummary fromDirectory(DirectoryTransfer dt, long confirmedBytes) {
        return TransferSummary.builder()
                .id(dt.getDirectoryTransferId())
                .dispatchId(dt.getDispatchId())
                .type(TransferType.DIRECTORY)
                .name(dt.getDirectoryName())
                .status(dt.getStatus().name())
                .totalSize(dt.getTotalSize())
                .confirmedBytes(confirmedBytes)
                .totalFiles(dt.getTotalFiles())
                .targetAgentId(dt.getTargetAgentId())
                .targetIp(dt.getTargetIp())
                .targetPort(dt.getTargetPort())
                .createdAt(dt.getCreatedAt())
                .completedAt(dt.getCompletedAt())
                .failureReason(dt.getFailureReason())
                .build();
    }
}