package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class LogSyncRequest {
    private UUID id;
    private UUID fromAgentId;
    private String fromAgentName;
    private UUID toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private Instant timestamp;
}