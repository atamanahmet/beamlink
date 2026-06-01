package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.GroupTransferStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "batch_transfer")
@Getter
@Setter
@NoArgsConstructor
public class BatchTransfer {

        @Id
        @Column(nullable = false, updatable = false)
        @JdbcTypeCode(12)
        private UUID batchTransferId;

        @Column
        @JdbcTypeCode(12)
        private UUID dispatchId;

        @Column(nullable = false)
        @JdbcTypeCode(12)
        private UUID sourceAgentId;
        @Column
        @JdbcTypeCode(12)
        private UUID targetAgentId;
        @Column
        private String targetIp;
        @Column
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
                        UUID dispatchId,
                        UUID sourceAgentId,
                        UUID targetAgentId,
                        String targetIp,
                        int targetPort,
                        int totalFiles,
                        long totalSize) {
                BatchTransfer bt = new BatchTransfer();
                bt.batchTransferId = batchTransferId;
                bt.dispatchId = dispatchId;
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