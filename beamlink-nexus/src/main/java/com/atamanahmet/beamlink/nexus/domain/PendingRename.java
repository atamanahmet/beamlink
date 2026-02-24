package com.atamanahmet.beamlink.nexus.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rename request waiting for approval
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pending_rename")
public class PendingRename {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String agentId;
    private String currentName;
    private String requestedName;
    private LocalDateTime requestedAt;
}
