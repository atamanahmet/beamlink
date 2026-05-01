package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batch_transfer")
@Getter
@Setter
@NoArgsConstructor
public class BatchTransfer {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "VARCHAR(36)")
    private UUID batchTransferId;

    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID sourceAgentId;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID targetAgentId;

    @Column()
    private String targetIp;

    @Column()
    private int targetPort;

    @Column(nullable = false)
    private int totalFiles;

    @Column(nullable = false)
    private long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupTransferStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;

    @Column
    private String failureReason;

    public static BatchTransfer initiate(
            UUID batchTransferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String targetIp,
            int targetPort,
            int totalFiles,
            long totalSize
    ) {
        BatchTransfer bt = new BatchTransfer();
        bt.batchTransferId = batchTransferId;
        bt.sourceAgentId = sourceAgentId;
        bt.targetAgentId = targetAgentId;
        bt.targetIp = targetIp;
        bt.targetPort = targetPort;
        bt.totalFiles = totalFiles;
        bt.totalSize = totalSize;
        bt.status = GroupTransferStatus.PENDING;
        bt.createdAt = Instant.now();
        return bt;
    }
}