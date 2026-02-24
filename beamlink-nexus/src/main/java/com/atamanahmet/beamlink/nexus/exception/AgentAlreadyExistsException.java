package com.atamanahmet.beamlink.nexus.exception;


import com.atamanahmet.beamlink.nexus.domain.Agent;
import lombok.Getter;

public class AgentAlreadyExistsException extends RuntimeException {

    @Getter
    private final Agent agent;

    public AgentAlreadyExistsException(Agent agent) {
        super("Agent already registered: " + agent.getIpAddress() + ":" + agent.getPort());
        this.agent = agent;
    }

}