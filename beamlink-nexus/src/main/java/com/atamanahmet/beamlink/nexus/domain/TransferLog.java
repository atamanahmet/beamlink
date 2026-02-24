package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Received transfer log from all agents
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transfer_logs")
@Builder
public class TransferLog {
    @Id
    private UUID id;

    private UUID fromAgentId;
    private String fromAgentName;
    private UUID toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private Instant timestamp;
}