package com.atamanahmet.beamlink.agent.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferLogDTO {
    private UUID id;
    private UUID fromAgentId;
    private String fromAgentName;
    private UUID toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private Instant timestamp;
    private double averageSpeedMbps;
}