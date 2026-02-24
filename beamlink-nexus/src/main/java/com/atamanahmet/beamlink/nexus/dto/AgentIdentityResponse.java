package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class AgentIdentityResponse {
    private UUID agentId;
    private String agentName;
    private String authToken;
    private String publicToken;
    private AgentState state;
}
