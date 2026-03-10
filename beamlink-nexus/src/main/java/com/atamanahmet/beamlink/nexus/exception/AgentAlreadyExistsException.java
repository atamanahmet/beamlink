package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AgentAlreadyExistsException extends RuntimeException {
    private final UUID agentId;
    private final AgentState state;

    public AgentAlreadyExistsException(UUID agentId, AgentState state) {
        super("Agent already exists: " + agentId);
        this.agentId = agentId;
        this.state = state;
    }
}