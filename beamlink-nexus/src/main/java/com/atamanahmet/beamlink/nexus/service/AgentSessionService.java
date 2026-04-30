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

    public void markOffline(UUID agentId) {
        agentRepository.findById(agentId).ifPresent(agent -> {
            agent.setLastSeenAt(Instant.EPOCH);
            agentRepository.save(agent);
        });
    }

    public AgentDTO toDTO(Agent agent) {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(2));
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