package com.atamanahmet.beamlink.nexus.event;

import com.atamanahmet.beamlink.nexus.domain.Agent;

public record AgentApprovedEvent(Agent agent, String authToken, String publicToken) {}