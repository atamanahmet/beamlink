package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.GroupTransferStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "directory_transfer")
@Getter
@Setter
@NoArgsConstructor
public class DirectoryTransfer {

    @Id
    @Column(nullable = false, updatable = false, columnDefinition = "VARCHAR(36)")
    private UUID directoryTransferId;

    @Column(nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID sourceAgentId;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID targetAgentId;

    @Column()
    private String targetIp;

    @Column()
    private int targetPort;

    /* root folder name, used to reconstruct structure on receiver */
    @Column(nullable = false)
    private String directoryName;

    /* absolute path on source disk, null on receiver side */
    @Column
    private String sourcePath;

    @Column(nullable = false)
    private int totalFiles;

    @Column(nullable = false)
    private long totalSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupTransferStatus status;

    /* empty dirs that need to exist before any files arrive */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "directory_transfer_empty_dirs",
            joinColumns = @JoinColumn(name = "directory_transfer_id")
    )
    @Column(name = "dir_path")
    private List<String> emptyDirectories;

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant completedAt;

    @Column
    private String failureReason;

    public static DirectoryTransfer initiate(
            UUID directoryTransferId,
            UUID sourceAgentId,
            UUID targetAgentId,
            String targetIp,
            int targetPort,
            String directoryName,
            String sourcePath,
            int totalFiles,
            long totalSize,
            List<String> emptyDirectories
    ) {
        DirectoryTransfer dt = new DirectoryTransfer();
        dt.directoryTransferId = directoryTransferId;
        dt.sourceAgentId = sourceAgentId;
        dt.targetAgentId = targetAgentId;
        dt.targetIp = targetIp;
        dt.targetPort = targetPort;
        dt.directoryName = directoryName;
        dt.sourcePath = sourcePath;
        dt.totalFiles = totalFiles;
        dt.totalSize = totalSize;
        dt.emptyDirectories = emptyDirectories;
        dt.status = GroupTransferStatus.PENDING;
        dt.createdAt = Instant.now();
        return dt;
    }
}