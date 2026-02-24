//package com.atamanahmet.beamlink.nexus.service;
//
//import com.atamanahmet.beamlink.nexus.domain.Agent;
//import lombok.RequiredArgsConstructor;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.temporal.ChronoUnit;
//import java.util.List;
//
///**
// * Monitors agent health and marks offline agents
// */
//@Service
//@RequiredArgsConstructor
//public class MonitoringService {
//
//    private final Logger log = LoggerFactory.getLogger(MonitoringService.class);
//
//    private final AgentService agentService;
//    private static final int OFFLINE_THRESHOLD_MINUTES = 2;
//
//    /**
//     * Check agent health every minute
//     */
//    @Scheduled(fixedDelay = 60_000)
//    public void checkAgentHealth() {
//
//        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
//
//        List<Agent> staleAgents = agentService.getOnlineAgentsBefore(threshold);
//
//        for (Agent agent : staleAgents) {
//            agentService.saveAgent(agent);
//            log.info("Agent offline: {}", agent.getName());
//        }
//    }
//}