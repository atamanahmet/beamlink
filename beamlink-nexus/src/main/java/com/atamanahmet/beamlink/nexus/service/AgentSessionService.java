package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final AgentRepository agentRepository;

    public Agent findById(UUID agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new AgentNotFoundException("Unknown agent: " + agentId));
    }

    public Agent save(Agent agent) {
        return agentRepository.save(agent);
    }

    public List<Agent> findApproved() {
        return agentRepository.findByState(AgentState.APPROVED);
    }

    public AgentDTO toDTO(Agent agent) {

        Instant now = Instant.now();
        Instant threshold = now.minus(Duration.ofMinutes(2));
        Instant lastSeen = agent.getLastSeenAt();

        boolean agentOnline = agent.isOnline();

        boolean online = false;

        if (lastSeen != null) {
            online = lastSeen.isAfter(threshold);
        }

        return AgentDTO.builder()
                .id(agent.getId())
                .agentName(agent.getName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .online(online)
                .publicToken(agent.getPublicToken())
                .build();
    }
}