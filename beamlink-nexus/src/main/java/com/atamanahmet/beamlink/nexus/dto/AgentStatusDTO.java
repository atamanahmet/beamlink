package com.atamanahmet.beamlink.nexus.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class AgentStatusDTO {
    private UUID agentId;
    private String agentName;
    private boolean online;

    private String publicToken;
}
