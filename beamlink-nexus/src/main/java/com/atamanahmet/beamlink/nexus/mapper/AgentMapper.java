package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class AgentMapper {

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    public AgentDTO toDTO(Agent agent) {
        Instant threshold = Instant.now()
                .minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));

        Instant lastSeen = agent.getLastSeenAt();

        return AgentDTO.builder()
                .id(agent.getId())
                .agentName(agent.getName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .online(lastSeen != null && lastSeen.isAfter(threshold))
                .publicId(agent.getPublicId())
                .build();
    }
}