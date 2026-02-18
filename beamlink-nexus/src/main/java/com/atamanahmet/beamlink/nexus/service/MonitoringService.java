package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.repository.DataStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Monitors agent health and marks offline agents
 */
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final DataStore dataStore;
    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    /**
     * Check agent health every minute
     */
    @Scheduled(fixedDelay  = 60000)
    public void checkAgentHealth() {

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(OFFLINE_THRESHOLD_MINUTES);

        for (AgentRegistry agent : dataStore.getAllAgents()) {
            if (agent.getLastSeen() != null && agent.getLastSeen().isBefore(threshold) && agent.isOnline()) {
                agent.setOnline(false);
                dataStore.saveAgent(agent);
                log.info("Agent offline: {}", agent.getName());
            }
        }
    }
}