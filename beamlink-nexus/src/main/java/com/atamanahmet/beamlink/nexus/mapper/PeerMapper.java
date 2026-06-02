package com.atamanahmet.beamlink.nexus.mapper;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.PeerDTO;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.service.NexusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PeerMapper {

    private final NexusConfig nexusConfig;
    private final AgentTokenService agentTokenService;
    private final NexusService nexusService;

    public PeerDTO fromAgent(Agent agent) {
        String publicToken = agent.getPublicId() != null
                ? agentTokenService.generatePublicToken(agent.getId(), agent.getPublicId())
                : null;

        return PeerDTO.builder()
                .id(agent.getId())
                .agentName(agent.getName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .online(agent.isOnline())
                .publicId(agent.getPublicId())
                .publicToken(publicToken)
                .lastSeen(agent.getLastSeenAt() != null
                        ? agent.getLastSeenAt().toEpochMilli() : 0L)
                .build();
    }

    public PeerDTO nexusPeer() {
        return PeerDTO.builder()
                .id(nexusService.getNexusId())
                .agentName(nexusConfig.getName())
                .ipAddress(nexusConfig.getIp())
                .port(nexusConfig.getNexusPort())
                .publicId(nexusService.getNexusId())
                .publicToken(null)
                .online(true)
                .lastSeen(System.currentTimeMillis())
                .build();
    }
}
