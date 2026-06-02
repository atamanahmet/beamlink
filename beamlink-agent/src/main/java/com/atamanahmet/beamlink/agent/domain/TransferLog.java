package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.TransferSyncState;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;import java.util.UUID;

/**
 * Represents a file transfer event
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "transfer_logs"
)
@Builder
public class TransferLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    private UUID fromAgentId;
    private String fromAgentName;
    private UUID toAgentId;
    private String toAgentName;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long fileSize;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private double averageSpeedMbps;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferSyncState syncState;

}