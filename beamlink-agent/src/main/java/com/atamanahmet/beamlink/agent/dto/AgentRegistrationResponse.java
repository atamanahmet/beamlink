package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.AgentState;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class AgentRegistrationResponse {
    private UUID agentId;
    private AgentState agentState;
    private String authToken;
    private String publicToken;
}