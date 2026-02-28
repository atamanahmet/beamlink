package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NexusStatusService {

    private final Logger log = LoggerFactory.getLogger(NexusStatusService.class);

    private final AgentService agentService;
    private final NexusWebSocketService wsService;
    private final NexusEventPublisher nexusEventPublisher;
    private final NexusConnectionStateService connectionState;
    private final AgentConfig config;
    private final LogService logService;
    private final RegistrationService registrationService;

    private final WebClient nexusWebClient = WebClient.create();

    @EventListener
    public void onAgentApproved(AgentApprovedEvent event) {
        log.info("Agent approval received. Connecting WebSocket in 2s...");
        Mono.delay(Duration.ofSeconds(2))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(l -> log.info("Attempting post-approval WS connect..."))
                .doOnError(err -> log.warn("Post-approval WS connect failed: {}", err.getMessage()))
                .onErrorComplete()
                .subscribe(l -> wsService.connect(config.getNexusUrl()));
    }

    /**
     * Periodically checks if agent is registered/approved.
     * Only HTTP; status/log updates are skipped once WS is active.
     */
    @Scheduled(fixedRate = 30_000)
    public void ensureRegistered() {
        AgentState state = agentService.getState();

        if (state == AgentState.UNREGISTERED) {
            log.info("Agent unregistered. Attempting registration...");
            if (!isNexusReachable()) {
                connectionState.reportOffline();
                return;
            }
            connectionState.reportOnline(); // may fire NexusOnlineEvent → registerWithNexus
            if (registrationService.isRegistrationInProgress()) {
                log.debug("Registration already triggered by online event. Skipping.");
                return;
            }
            registrationService.registerWithNexus();
            return;
        }

        if (state == AgentState.PENDING_APPROVAL) {
            log.info("Agent pending approval. Checking if Nexus knows agent...");
            if (!isNexusReachable()) {
                connectionState.reportOffline();
                return;
            }
            connectionState.reportOnline();
            boolean known = checkIfNexusKnowsAgent();
            if (!known) {
                log.warn("Nexus does not recognize agent ID. Nexus DB may have been wiped.");
                nexusEventPublisher.publishLostAgent("Nexus lost agent record");
            }
            return;
        }

        if (state == AgentState.APPROVED && !wsService.isConnected()) {
            log.info("Agent approved but WS disconnected. Reconnecting...");
            wsService.connect(config.getNexusUrl());
        }
    }

    private boolean isNexusReachable() {
        try {
            nexusWebClient.get()
                    .uri(config.getNexusUrl() + "/api/agents/ping")
                    .retrieve()
                    .toBodilessEntity()
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.debug("Nexus ping failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkIfNexusKnowsAgent() {
        UUID agentId = agentService.getAgentId();
        if (agentId == null) {
            log.debug("No agent ID yet, skipping existence check.");
            return false;
        }

        try {
            nexusWebClient.get()
                    .uri(config.getNexusUrl() + "/api/agents/" + agentService.getAgentId() + "/exists")
                    .retrieve()
                    .toBodilessEntity()
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return true;
        } catch (Exception e) {
            log.warn("Nexus does not know agent: {}", e.getMessage());
            return false;
        }
    }

    /**
     * HTTP status report (skipped once WS is connected)
     */
    @Scheduled(fixedRate = 30_000)
    public void reportStatus() {
        if (!agentService.isApproved()) return;
        if (wsService.isConnected()) {
            log.debug("WebSocket active, skipping HTTP status report.");
            return;
        }

        try {
            nexusWebClient.post()
                    .uri(config.getNexusUrl() + "/api/agents/status")
                    .header("X-Auth-Token", agentService.getAuthToken())
                    .bodyValue(agentService.getAgentStatusDTO())
                    .retrieve()
                    .toBodilessEntity()
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();
            log.debug("HTTP status reported to Nexus (fallback)");
        } catch (WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 401 || status == 403) {
                log.warn("HTTP status rejected [{}]. Publishing lost-agent event.", status);
                nexusEventPublisher.publishLostAgent("HTTP status rejected with " + status);
            } else {
                log.warn("Unexpected HTTP status response [{}]", status);
            }
        } catch (Exception e) {
            log.warn("HTTP status report failed: {}", e.getMessage());
        }
    }

    /**
     * HTTP log sync (skipped once WS is connected)
     */
    @Scheduled(fixedRate = 60_000)
    public void syncLogsToNexus() {
        if (!agentService.isApproved()) return;

        try {
            List<TransferLog> unsyncedLogs = logService.getUnsyncedLogs();
            if (unsyncedLogs.isEmpty()) return;

            nexusWebClient.post()
                    .uri(config.getNexusUrl() + "/api/logs/sync")
                    .header("X-Auth-Token", agentService.getAuthToken())
                    .bodyValue(unsyncedLogs)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(Duration.ofSeconds(5))
                    .block();

            List<String> syncedIds = unsyncedLogs.stream()
                    .map(TransferLog::getId)
                    .toList();
            logService.markAsSynced(syncedIds);

            log.debug("HTTP logs synced to Nexus ({} logs)", unsyncedLogs.size());

        } catch (WebClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 404 || status == 401 || status == 403) {
                log.warn("HTTP log sync rejected [{}]. Publishing lost-agent event.", status);
                nexusEventPublisher.publishLostAgent("HTTP log sync rejected with " + status);
            } else {
                log.warn("Unexpected HTTP log sync response [{}]", status);
            }
        } catch (Exception e) {
            log.warn("HTTP log sync failed: {}", e.getMessage());
        }
    }
}