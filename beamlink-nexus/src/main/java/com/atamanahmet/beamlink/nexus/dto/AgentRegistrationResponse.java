package com.atamanahmet.beamlink.nexus.dto;


import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;

import java.util.UUID;

public record AgentRegistrationResponse(UUID agentId, AgentState agentState, String authToken , String publicToken) {
}
