package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.AgentState;
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
