package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.config.NexusAddressHolder;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationRequest;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import com.atamanahmet.beamlink.agent.event.NexusLostAgentEvent;
import com.atamanahmet.beamlink.agent.event.NexusOfflineEvent;
import com.atamanahmet.beamlink.agent.event.NexusOnlineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkRegistrationService {

    private final AgentConfig agentConfig;
    private final AgentService agentService;
    private final WebClient nexusWebClient;
    private final NexusEventPublisher nexusEventPublisher;
    private final NexusAddressHolder nexusAddressHolder;

    private final AtomicBoolean registrationInProgress = new AtomicBoolean(false);

    /** Reset agent and re-register when nexus lost agent's record. */
    @Async
    @EventListener
    public void onNexusLostAgent(NexusLostAgentEvent event) {
        log.warn("NexusLostAgentEvent received, reason: {}. Forcing reset and re-registering.", event.reason());
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

    public void registerWithNexus() {
        if (!nexusAddressHolder.isResolved()) {
            log.warn("Nexus URL not resolved. Cannot register.");
            return;
        }

        if (!registrationInProgress.compareAndSet(false, true)) {
            log.info("Registration already in progress. Skipping.");
            return;
        }

        try {
            AgentRegistrationResponse result = nexusWebClient.post()
                    .uri(nexusAddressHolder.getNexusUrl() + "/api/agents/register")
                    .bodyValue(buildRegistrationRequest())
                    .retrieve()
                    .bodyToMono(AgentRegistrationResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.warn("Registration rejected by Nexus [{}]: {}", ex.getStatusCode().value(), ex.getMessage());
                        return Mono.empty();
                    })
                    .onErrorResume(ex -> {
                        log.warn("Nexus unreachable during registration: {}. Will retry.", ex.getMessage());
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (result == null) {
                log.warn("Registration returned empty response. Will retry next cycle.");
                return;
            }

            agentService.updateAgentId(result.getAgentId());
            agentService.transitionTo(result.getAgentState());
            log.info("✓ Registered with Nexus [id={}]. State={}", result.getAgentId(), result.getAgentState());

            /* Already approved on nexus side, get fresh tokens */
            if (result.getAgentState() == AgentState.APPROVED) {
                log.info("Agent already approved on Nexus. Resolving identity to get fresh tokens.");
                resolveIdentityFromNexus();
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
        if (!nexusAddressHolder.isResolved()) {
            log.warn("Nexus URL not resolved. Cannot resolve identity.");
            return;
        }

        try {
            String uri = nexusAddressHolder.getNexusUrl()
                    + "/api/agents/identify?ipAddress=" + agentConfig.getIp()
                    + "&port=" + agentConfig.getAgentPort();

            AgentIdentityResponse identity = nexusWebClient.get()
                    .uri(uri)
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
                    .onErrorResume(ex -> {
                        log.warn("Nexus unreachable on startup. Will retry next interval.");
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (identity != null) {
                agentService.applyNexusIdentity(identity);
                log.info("✓ Identity resolved from Nexus. Name={}, State={}", identity.getAgentName(), identity.getState());
            }

        } catch (Exception e) {
            log.warn("Failed to resolve identity: {}. Will retry.", e.getMessage());
        }
    }

    public boolean isRegistrationInProgress() {
        return registrationInProgress.get();
    }
}