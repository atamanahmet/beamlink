package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentStatusDTO;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.agent.event.AgentIdentityChangedEvent;
import com.atamanahmet.beamlink.agent.repository.AgentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentConfig config;
    private final AgentRepository agentRepository;
    private final LogService logService;
    private final PeerCacheService peerCacheService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${server.port}")
    private int SERVER_PORT;

    @Value("${agent.ip-address}")
    private String SERVER_ADDRESS;

    /* In-memory cache, in sync with DB */
    private Agent agent;

    @PostConstruct
    public synchronized void init() {
        agent = agentRepository.findById(1L).orElseGet(() -> {
            Agent a = new Agent();
            a.setAgentName("Agent-" + SERVER_ADDRESS + ":" + SERVER_PORT);
            a.setIpAddress(SERVER_ADDRESS);
            a.setPort(SERVER_PORT);
            a.setState(AgentState.UNREGISTERED);
            log.info("No agent record found, creating new agent: {}", a.getAgentName());
            return agentRepository.save(a);
        });

        log.info("Agent loaded: name={}, agentId={}, state={}",
                agent.getAgentName(), agent.getAgentId(), agent.getState());
    }

    /* Persist and keep cache in sync */
    private synchronized void persist() {
        agent = agentRepository.save(agent);
    }

    public synchronized void transitionTo(AgentState newState) {
        AgentState current = agent.getState();

        if (current == AgentState.APPROVED && newState == AgentState.UNREGISTERED) {
            log.warn("Ignoring invalid state transition {} -> {}", current, newState);
            return;
        }

        if (newState == AgentState.PENDING_APPROVAL) {
            agent.setAuthToken(null);
            agent.setPublicToken(null);
            log.debug("Tokens cleared on transition to PENDING_APPROVAL.");
        }

        log.info("Agent state: {} -> {}", current, newState);
        agent.setState(newState);
        persist();
    }

    public synchronized void forceReset() {
        if (agent.getState() == AgentState.UNREGISTERED && agent.getAgentId() == null) {
            log.debug("Agent already in clean UNREGISTERED state, skipping reset.");
            return;
        }
        agent.setAgentId(null);
        agent.setState(AgentState.UNREGISTERED);
        agent.setAuthToken(null);
        agent.setPublicToken(null);
        agent.setAgentName(getAgentName());
        persist();
        log.info("Agent force reset to UNREGISTERED.");
    }

    /** Stores both tokens received from Nexus and persists to DB. */
    public synchronized void storeTokens(String authToken, String publicToken) {
        agent.setAuthToken(authToken);
        agent.setPublicToken(publicToken);
        persist();
        log.debug("Tokens stored.");
    }

    /** Called when nexus pushes approval to this agent. */
    public synchronized void applyNexusIdentity(ApprovalPushRequest request) {
        agent.setAgentId(request.getAgentId());
        agent.setAgentName(request.getApprovedName());
        agent.setAuthToken(request.getAuthToken());
        agent.setPublicToken(request.getPublicToken());
        agent.setState(request.getState());
        persist();
        log.info("Agent approved. Name={}, AgentId={}", agent.getAgentName(), agent.getAgentId());

        eventPublisher.publishEvent(new AgentApprovedEvent(this));
    }

    public synchronized void applyNexusIdentity(AgentIdentityResponse response) {
        boolean wasAlreadyApproved = agent.getState() == AgentState.APPROVED
                && agent.getAgentId() != null
                && agent.getAgentId().equals(response.getAgentId());

        boolean identityChanged = !response.getAgentId().equals(agent.getAgentId())
                || !response.getAgentName().equals(agent.getAgentName())
                || !Objects.equals(response.getAuthToken(), agent.getAuthToken())
                || !Objects.equals(response.getPublicToken(), agent.getPublicToken())
                || response.getState() != agent.getState();

        agent.setAgentId(response.getAgentId());
        agent.setAgentName(response.getAgentName());
        agent.setAuthToken(response.getAuthToken());
        agent.setPublicToken(response.getPublicToken());
        agent.setState(response.getState());
        persist();
        log.info("Identity applied. Name={}, AgentId={}, changed={}",
                agent.getAgentName(), agent.getAgentId(), identityChanged);

        if (!wasAlreadyApproved && response.getState() == AgentState.APPROVED) {
            eventPublisher.publishEvent(new AgentApprovedEvent(this));
        } else if (identityChanged) {
            eventPublisher.publishEvent(new AgentIdentityChangedEvent(this));
        }
    }

    public synchronized void updateAgentName(String newName) {
        agent.setAgentName(newName);
        persist();
        log.info("Agent renamed to: {}", newName);
    }

    public synchronized void updateAgentId(UUID newAgentId) {
        agent.setAgentId(newAgentId);
        persist();
        log.info("Agent updated agentId to: {}", newAgentId);
    }

    public boolean isApproved() {
        return agent.isApproved();
    }

    public AgentState getState() {
        return agent.getState();
    }

    /* Returns the nexus assigned UUID */
    public UUID getAgentId() {
        return agent.getAgentId();
    }

    public String getAgentName() {
        return agent.getAgentName();
    }

    public String getAuthToken() {
        return agent.getAuthToken();
    }

    public String getPublicToken() {
        return agent.getPublicToken();
    }

    public Agent getAgent() {
        return agent;
    }

    public AgentStatusDTO getAgentStatusDTO() {
        return AgentStatusDTO.builder()
                .agentId(agent.getAgentId())
                .agentName(agent.getAgentName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .unSyncedLogs(logService.getUnsyncedLogs().size())
                .peerVersion(peerCacheService.getCurrentPeerListVersion())
                .build();
    }
}