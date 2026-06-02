package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.config.NexusAddressHolder;
import com.atamanahmet.beamlink.agent.config.NexusClientConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentRenameRequest;
import com.atamanahmet.beamlink.agent.dto.AgentStatusDTO;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.agent.event.AgentIdentityChangedEvent;
import com.atamanahmet.beamlink.agent.exception.NexusOfflineException;
import com.atamanahmet.beamlink.agent.repository.AgentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.scheduler.Schedulers;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentConfig config;
    private final AgentRepository agentRepository;
    private final TransferLogService transferLogService;
    private final PeerCacheService peerCacheService;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final NexusAddressHolder nexusAddressHolder;
    private final NexusClientConfig nexusClientConfig;

    private Agent agent;

    /**
     * Load agent from db on startup, create if not exists
     */
    @PostConstruct
    public synchronized void init() {
        this.agent = agentRepository.findById(1L).orElseGet(() -> {
            Agent a = new Agent();
            a.setAgentName("Agent-" + config.getIp() + ":" + config.getAgentPort());
            a.setIpAddress(config.getIp());
            a.setPort(config.getAgentPort());
            a.setState(AgentState.UNREGISTERED);
            a.setUsername(config.getAgentUsername());
            a.setEncodedPassword(passwordEncoder.encode(config.getAgentPassword()));
            a.setCredentialsConfigured(false);
            log.info("No agent record found, creating new agent: {}", a.getAgentName());
            return agentRepository.save(a);
        });
        log.info("Agent loaded: name={}, agentId={}, state={}, credentialsConfigured={}",
                agent.getAgentName(), agent.getAgentId(), agent.getState(), agent.isCredentialsConfigured());
    }

    private synchronized void persist() {
        this.agent = agentRepository.save(agent);
    }

    public synchronized void transitionTo(AgentState newState) {
        AgentState current = agent.getState();

        // don't allow going back UNREGISTERED once approved
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
        persist();
        log.info("Agent force reset to UNREGISTERED.");
    }

    public synchronized void storeTokens(String authToken, String publicToken) {
        agent.setAuthToken(authToken);
        agent.setPublicToken(publicToken);
        persist();
        log.debug("Tokens stored.");
    }

    /**
     * Called when nexus pushes approval directly to this agent
     */
    public synchronized void applyNexusIdentity(ApprovalPushRequest request) {
        agent.setAgentId(request.getAgentId());
        agent.setAgentName(request.getApprovedName());
        agent.setAuthToken(request.getAuthToken());
        agent.setPublicToken(request.getPublicToken());
        agent.setPublicId(request.getPublicId());
        agent.setState(request.getState());
        agent.setNexusPublicKey(request.getNexusPublicKey());
        persist();
        log.info("Agent approved. Name={}, AgentId={}", agent.getAgentName(), agent.getAgentId());
        eventPublisher.publishEvent(new AgentApprovedEvent(this));
    }

    /**
     * Called when agent polls nexus and gets identity back
     */
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
        agent.setPublicId(response.getPublicId());
        agent.setState(response.getState());

        if (response.getNexusPublicKey() != null) {
            agent.setNexusPublicKey(response.getNexusPublicKey());
        }

        persist();
        log.info("Identity applied. Name={}, AgentId={}, changed={}",
                agent.getAgentName(), agent.getAgentId(), identityChanged);

        if (!wasAlreadyApproved && response.getState() == AgentState.APPROVED) {
            eventPublisher.publishEvent(new AgentApprovedEvent(this));
        } else if (identityChanged) {
            eventPublisher.publishEvent(new AgentIdentityChangedEvent(this));
        }
    }

    public void requestRename(UUID agentId, String newName) {
        String nexusUrl = nexusAddressHolder.getNexusUrl();
        if (nexusUrl == null) {
            throw new NexusOfflineException("Nexus not reachable, URL not resolved");
        }

        nexusClientConfig.nexusWebClient().post()
                .uri(nexusUrl + "/api/nexus/agent/" + agentId + "/rename")
                .header("X-Auth-Token", getAuthToken())
                .bodyValue(new AgentRenameRequest(newName))
                .retrieve()
                .toBodilessEntity()
                .subscribeOn(Schedulers.boundedElastic())
                .block();
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

    public synchronized void updateCredentials(String newUsername, String newEncodedPassword) {
        agent.setUsername(newUsername);
        agent.setEncodedPassword(newEncodedPassword);
        persist();
        log.info("Agent credentials updated. New username: {}", newUsername);
    }

    public synchronized void markRegistered() {
        agent.setCredentialsConfigured(true);
        persist();
        log.info("Agent UI credentials marked as configured.");
    }

    public boolean isRegistered() {
        return agent.isCredentialsConfigured();
    }

    public boolean isApproved() {
        return agent.isApproved();
    }

    public AgentState getState() {
        return agent.getState();
    }

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

    public String getUsername() {
        return agent.getUsername();
    }

    public String getEncodedPassword() {
        return agent.getEncodedPassword();
    }
    public String getAgentIp() {return agent.getIpAddress();}
    public Integer getAgentPort() {return agent.getPort();}

    public Agent getAgent() {
        return agent;
    }

    public AgentStatusDTO getAgentStatusDTO() {
        return AgentStatusDTO.builder()
                .agentId(agent.getAgentId())
                .agentName(agent.getAgentName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .unSyncedLogs(transferLogService.getUnsyncedLogs().size())
                .peerVersion(peerCacheService.getCurrentPeerListVersion())
                .build();
    }

    public String getNexusPublicKey() {
        return agent.getNexusPublicKey();
    }

    public UUID getPublicId() {
        return agent.getPublicId();
    }
}