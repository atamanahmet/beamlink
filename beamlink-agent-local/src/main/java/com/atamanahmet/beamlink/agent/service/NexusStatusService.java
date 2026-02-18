package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.exception.NexusOfflineException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports agent status to nexus periodically
 */
@Service
@RequiredArgsConstructor
public class NexusStatusService {

    private final AgentConfig config;
    private final LogService logService;
    private final AgentInfoService agentInfoService;
    private final PeerCacheService peerCacheService;
    private final Logger log = LoggerFactory.getLogger(NexusStatusService.class);

    /**
     * Send status update to nexus every 30 seconds
     */
    @Scheduled(fixedRate = 30000)
    public void reportStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("agentId", agentInfoService.getAgentId());
            status.put("agentName", agentInfoService.getAgentName());
            status.put("status", "online");
            status.put("unsyncedLogs", logService.getUnsyncedLogs().size());
            status.put("peerVersion", peerCacheService.getCurrentPeerListVersion());

            WebClient client = WebClient.create();
            String url = config.getNexusUrl() + "/api/agents/status";

            Map<String, Object> response = client.post()
                    .uri(url)
                    .bodyValue(status)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .doOnError(error -> {
                        log.warn("Nexus is offline. Continue with peer cache");
                    })
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response != null) {

                Object renameApproved = response.get("renameApproved");
                Object newName = response.get("newName");

                if (Boolean.TRUE.equals(renameApproved) && newName instanceof String) {

                    String currentName = agentInfoService.getAgentName();
                    String renamedTo = (String) newName;

                    if (!currentName.equals(renamedTo)) {
                        agentInfoService.updateAgentName(renamedTo);
                        log.info("✓ Agent renamed to: {}", renamedTo);
                    }
                }

                // Update in-memory peer statuses (lightweight)
                List<Map<String, Object>> agentStatuses =
                        (List<Map<String, Object>>) response.get("agentStatuses");

                if (agentStatuses != null) {
                    peerCacheService.updatePeerStatuses(agentStatuses);
                }

                // Check if peer list structure is outdated
                Boolean peerOutdated = (Boolean) response.get("peerOutdated");
                if (Boolean.TRUE.equals(peerOutdated)) {
                    log.info("Peer list outdated, refreshing...");
                    peerCacheService.refreshPeersFromNexus();
                }

                // Nexus online, sync logs
                syncLogsToNexus();
            }

        } catch (Exception e) {
            log.warn("Failed to report status: {}. Continue with peer cache", e.getMessage());
        }
    }

    /**
     * Sync unsynced logs to nexus
     */
    private void syncLogsToNexus() {

        List<TransferLog> unsyncedLogs = logService.getUnsyncedLogs();

        if (unsyncedLogs.isEmpty()) {
            return;
        }

        try {
            WebClient client = WebClient.create();
            String url = config.getNexusUrl() + "/api/logs/sync";

            Map<String, Object> response = client.post()
                    .uri(url)
                    .bodyValue(unsyncedLogs)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .onErrorResume(error -> Mono.empty())
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                List<String> mergedIds = (List<String>) response.get("mergedLogIds");
                logService.markAsSynced(mergedIds);

                log.info("✓ Synced {} logs to nexus", mergedIds.size());
            }

        } catch (Exception e) {
            log.debug("Failed to sync logs: {}", e.getMessage());
        }
    }
}