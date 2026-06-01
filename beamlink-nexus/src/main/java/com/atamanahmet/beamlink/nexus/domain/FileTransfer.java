package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_transfer")
@Getter
@Setter
@NoArgsConstructor
public class FileTransfer {

    @Id
    @Column(nullable = false, updatable = false)
    @JdbcTypeCode(12)
    private UUID transferId;

    @Column
    @JdbcTypeCode(12)
    private UUID directoryTransferId;

    @Column
    @JdbcTypeCode(12)
    private UUID batchTransferId;

    @Column(nullable = false)
    @JdbcTypeCode(12)
    private UUID sourceAgentId;

    @Column
    @JdbcTypeCode(12)
    private UUID targetAgentId;

    @Column(nullable = true)
    private String sourceIp;

    @Column(nullable = true)
    private Integer sourcePort;

    @Column
    private String targetIp;

    @Column
    private Integer targetPort;

    @Column(nullable = false)
    private String fileName;

    private String filePath;

    @Column(nullable = false)
    private long fileSize;

    @Column
    private String relativePath;

    @Column
    private String directoryName;

    @Column(nullable = false)
    private long confirmedOffset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private int maxRetries;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant lastChunkAt;

    @Column(nullable = false)
    private long activeTransferMs;

    @Column
    private Instant expiresAt;

    @Column
    private String failureReason;

    public static FileTransfer initiate(
            UUID transferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String fileName,
            String filePath,
            long fileSize) {
        FileTransfer ft = new FileTransfer();
        ft.transferId = transferId;
        ft.sourceAgentId = sourceAgentId;
        ft.targetAgentId = targetAgentId;
        ft.fileName = fileName;
        ft.filePath = filePath;
        ft.fileSize = fileSize;
        ft.confirmedOffset = 0L;
        ft.status = TransferStatus.PENDING;
        ft.retryCount = 0;
        ft.maxRetries = 5;
        ft.createdAt = Instant.now();
        return ft;
    }
}