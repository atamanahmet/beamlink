package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_transfer")
@Getter
@Setter
@NoArgsConstructor
public class FileTransfer {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "VARCHAR(36)")
    private UUID transferId;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID directoryTransferId;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID batchTransferId;

    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID sourceAgentId;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID targetAgentId;

    @Column
    private String targetIp;

    @Column
    private int targetPort;

    @Column(nullable = false)
    private String fileName;

    private String filePath;        // full path on source agent disk

    @Column(nullable = false)
    private long fileSize;

    /* relative path from directory root, only set for directory transfer children */
    @Column
    private String relativePath;

    /* root folder name from source, used to reconstruct directory structure on receiver */
    @Column
    private String directoryName;

    @Column(nullable = false)
    private long confirmedOffset;   // bytes written to disk on target

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
    private Instant lastChunkAt;    // last time a chunk was received

    @Column
    private Instant expiresAt;      // when EXPIRED status triggers

    @Column
    private String failureReason;   // last error, for UI and logs

    public static FileTransfer initiate(
            UUID transferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String fileName,
            String filePath,
            long fileSize
    ) {
        FileTransfer ft = new FileTransfer();
        ft.transferId = transferId;
        ft.sourceAgentId = sourceAgentId;
        ft.targetAgentId = targetAgentId;
        ft.fileName = fileName;
        ft.filePath = filePath;
        ft.fileSize = fileSize;
        ft.confirmedOffset = 0;
        ft.status = TransferStatus.PENDING;
        ft.retryCount = 0;
        ft.maxRetries = 5;
        ft.createdAt = Instant.now();
        return ft;
    }
}