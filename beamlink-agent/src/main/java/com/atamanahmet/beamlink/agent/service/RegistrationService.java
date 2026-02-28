package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationRequest;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import com.atamanahmet.beamlink.agent.event.NexusLostAgentEvent;
import com.atamanahmet.beamlink.agent.event.NexusOfflineEvent;
import com.atamanahmet.beamlink.agent.event.NexusOnlineEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final AgentConfig config;
    private final AgentService agentService;
    private final WebClient nexusWebClient;
    private final NexusEventPublisher nexusEventPublisher;

    private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);

    @Async
    @EventListener
    public void onNexusLostAgent(NexusLostAgentEvent event) {
        log.warn("NexusLostAgentEvent received — reason: {}. Forcing reset and re-registering.", event.reason());
        agentService.forceReset();
        registerWithNexus();
    }

    @EventListener
    public void onNexusOffline(NexusOfflineEvent event) {
        log.info("NexusOfflineEvent received. Registration will verify on reconnect.");
    }

    @EventListener
    public void onNexusOnline(NexusOnlineEvent event) {
        log.info("NexusOnlineEvent received. Checking registration state...");
        AgentState state = agentService.getState();
        if (state == AgentState.UNREGISTERED) {
            log.info("Agent unregistered. Re-registering after Nexus reconnect.");
            registerWithNexus();
        } else {
            log.debug("Agent state is {}. No re-registration needed.", state);
        }
    }

    /**
    * Registers agent with nexus and transitions state to PENDING_APPROVAL.
    * Nexus will push approval async with ApprovalController.
     * */
    public void registerWithNexus() {
        if (!registrationInProgress.compareAndSet(false, true)) {
            log.info("Registration already in progress. Skipping.");
            return;
        }
        try {
            AgentRegistrationResponse result = nexusWebClient
                    .post()
                    .uri("/api/agents/register")
                    .bodyValue(buildRegistrationRequest())
                    .retrieve()
                    .bodyToMono(AgentRegistrationResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Registration rejected by Nexus [{}]: {}", ex.getStatusCode().value(), ex.getMessage());
                        return Mono.empty();
                    })
                    .onErrorResume(e -> {
                        log.warn("Nexus unreachable during registration: {}. Will retry.", e.getMessage());
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (result != null) {
                agentService.updateAgentId(result.getAgentId());
                if (result.getAuthToken() != null) {
                    agentService.storeTokens(result.getAuthToken(), result.getPublicToken());
                }
                agentService.transitionTo(result.getAgentState());
                log.info("✓ Registered with Nexus [id={}]. State={}", result.getAgentId(), result.getAgentState());
            } else {
                log.warn("Registration returned empty response. Will retry next cycle.");
            }
        } catch (Exception e) {
            log.warn("Registration failed: {}. Will retry next cycle.", e.getMessage());
        } finally {
            registrationInProgress.set(false);
        }
    }

    private AgentRegistrationRequest buildRegistrationRequest() {
        Agent agent = agentService.getAgent();
        return AgentRegistrationRequest.builder()
                .agentName(agent.getAgentName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .build();
    }

    public void resolveIdentityFromNexus() {
        try {
            AgentIdentityResponse identity = nexusWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/agents/identify")
                            .queryParam("ipAddress", config.getIpAddress())
                            .queryParam("port", config.getPort())
                            .build())
                    .retrieve()
                    .bodyToMono(AgentIdentityResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        if (ex.getStatusCode().value() == 404) {
                            log.info("No existing identity on Nexus. Registering fresh.");
                            registerWithNexus();
                        } else {
                            log.warn("Identify failed [{}]. Will retry on next interval.", ex.getStatusCode().value());
                        }
                        return Mono.empty();
                    })
                    .onErrorResume(e -> {
                        log.warn("Nexus unreachable on startup. Will retry next interval.");
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (identity != null) {
                agentService.applyNexusIdentity(identity);
                log.info("✓ Identity resolved from Nexus. Name={}, State={}",
                        identity.getAgentName(), identity.getState());
            }
        } catch (Exception e) {
            log.warn("Failed to resolve identity: {}. Will retry.", e.getMessage());
        }
    }

    public boolean isRegistrationInProgress() {
        return registrationInProgress.get();
    }
}

