package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentStatusDTO;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final Logger log = LoggerFactory.getLogger(AgentService.class);

    private static final String INFO_FILE = "agent_info.json";
    private static final String INFO_FILE_TMP = "agent_info.json.tmp";

    private final AgentConfig config;
    private final LogService logService;
    private final PeerCacheService peerCacheService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${server.port}")
    private int SERVER_PORT;

    @Value("${agent.ip-address}")
    private String SERVER_ADDRESS;

    @Getter
    private Agent agent;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    @PostConstruct
    public void init() {
        File file = new File(INFO_FILE);
        if (file.exists()) {
            loadFromFile(file);
        } else {
            createNewAgent();
        }
    }

    private void createNewAgent() {
        agent = new Agent();
        agent.setAgentName("Agent-" + SERVER_ADDRESS + ":" + SERVER_PORT);
        agent.setIpAddress(SERVER_ADDRESS);
        agent.setPort(SERVER_PORT);
        agent.setState(AgentState.UNREGISTERED);
        saveToFile();
        log.info("Generated new agent: {}", agent.getAgentName());
        log.info("IP address: {}", agent.getIpAddress());
    }

    private void loadFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            agent = gson.fromJson(reader, Agent.class);
            if (agent == null || agent.getAgentName() == null) {
                log.warn("Corrupted agent file. Creating new agent.");
                createNewAgent();
            } else {
                log.info("Loaded agent: name={}, id={}, state={}",
                        agent.getAgentName(), agent.getId(), agent.getState());
            }
        } catch (Exception e) {
            log.warn("Failed to read agent file: {}. Creating new agent.", e.getMessage());
            createNewAgent();
        }
    }

    /**
     * Writes to a temp file first, then renames.
     * Prevents corrupted state if process dies mid-write.
     */
    private synchronized void saveToFile() {
        try {
            Path tmp    = Path.of(INFO_FILE_TMP);
            Path target = Path.of(INFO_FILE);
            Files.writeString(tmp, gson.toJson(agent));
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to save agent state to file: {}", e.getMessage());
        }
    }


    public synchronized void transitionTo(AgentState newState) {
        AgentState current = agent.getState();
        // Guard invalid transitions
        if (current == AgentState.APPROVED && newState == AgentState.UNREGISTERED) {
            log.warn("Ignoring invalid state transition {} -> {}", current, newState);
            return;
        }
        log.info("Agent state: {} -> {}", current, newState);
        agent.setState(newState);
        saveToFile();
    }

    public synchronized void forceReset() {
        if (agent.getState() == AgentState.UNREGISTERED && agent.getId() == null) {
            log.debug("Agent already in clean UNREGISTERED state, skipping reset.");
            return;
        }
        agent.setId(null);
        agent.setState(AgentState.UNREGISTERED);
        agent.setAuthToken(null);
        agent.setPublicToken(null);
        agent.setAgentName(getAgentName());
        saveToFile();
        log.info("Agent force reset to UNREGISTERED.");
    }

    /**
     * Stores both tokens received from Nexus and persists to file.
     */
    public synchronized void storeTokens(String authToken, String publicToken) {
        agent.setAuthToken(authToken);
        agent.setPublicToken(publicToken);
        saveToFile();
        log.debug("Tokens stored.");
    }

    /**
     * Called when nexus pushes approval to this agent.
     */
    public synchronized void applyNexusIdentity(ApprovalPushRequest request) {
        agent.setId(request.getAgentId());
        agent.setAgentName(request.getApprovedName());
        agent.setAuthToken(request.getAuthToken());
        agent.setPublicToken(request.getPublicToken());
        agent.setState(request.getState());
        saveToFile();
        log.info("✓ Agent approved. Name={}, Id={}", agent.getAgentName(),agent.getId());

        eventPublisher.publishEvent(new AgentApprovedEvent(this));
    }

    public synchronized void applyNexusIdentity(AgentIdentityResponse response) {
        agent.setId(response.getAgentId());
        agent.setAgentName(response.getAgentName());
        agent.setAuthToken(response.getAuthToken());
        agent.setPublicToken(response.getPublicToken());
        agent.setState(response.getState());
        saveToFile();
        log.info("✓ Agent approved. Name={}, Id={}", agent.getAgentName(),agent.getId());

        eventPublisher.publishEvent(new AgentApprovedEvent(this));
    }


    public synchronized void updateAgentName(String newName) {
        agent.setAgentName(newName);
        saveToFile();
        log.info("Agent renamed to: {}", newName);
    }
    public synchronized void updateAgentId(UUID newAgentId) {
        agent.setId(newAgentId);
        saveToFile();
        log.info("Agent updated id to: {}", newAgentId);
    }

    public boolean isApproved() {
        return agent.isApproved();
    }

    public AgentState getState() {
        return agent.getState();
    }

    public UUID getAgentId() {
        return agent.getId();
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

    public AgentStatusDTO getAgentStatusDTO() {
        return AgentStatusDTO.builder()
                .agentId(agent.getId())
                .agentName(agent.getAgentName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .status(agent.isOnline())
                .unSyncedLogs(logService.getUnsyncedLogs().size())
                .peerVersion(peerCacheService.getCurrentPeerListVersion())
                .build();
    }
}