package com.atamanahmet.beamlink.nexus.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Received transfer log from all agents
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferLog {

    private String id;
    private String fromAgentId;
    private String fromAgentName;
    private String toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private LocalDateTime timestamp;
}