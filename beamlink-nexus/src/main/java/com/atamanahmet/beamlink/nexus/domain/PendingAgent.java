package com.atamanahmet.beamlink.nexus.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Agent waiting for approval
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingAgent {

    private String agentId;
    private String name;
    private String ipAddress;
    private int port;
    private LocalDateTime requestedAt;
}