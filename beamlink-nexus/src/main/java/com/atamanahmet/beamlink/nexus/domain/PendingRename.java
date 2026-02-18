package com.atamanahmet.beamlink.nexus.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Rename request waiting for approval
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingRename {

    private String agentId;
    private String currentName;
    private String requestedName;
    private LocalDateTime requestedAt;
}
