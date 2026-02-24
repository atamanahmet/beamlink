package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.exception.AgentAlreadyExistsException;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.exception.NameAlreadyInUseException;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;

import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRepository agentRepository;
    private final PeerListService peerListService;
    private final AgentTokenService agentTokenService;
    private final AgentPushService agentPushService;

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    /**
     * Agent registration
     * Checks if agent at ip:port exist in db
     * Returns created id of agent and approval state
     */
    @Transactional
    public AgentRegistrationResponse register(AgentRegistrationRequest request) {

        Optional<Agent> existing = agentRepository.findByIpAddressAndPort(request.getIpAddress(), request.getPort());
        if (existing.isPresent()) {
            log.info("Agent already registered: {}. Returning existing record.", existing.get().getId());
            return new AgentRegistrationResponse(existing.get().getId(), existing.get().getState());
        }

        Agent agent = Agent.builder()
                .name(request.getIpAddress()+":"+request.getPort())
                .ipAddress(request.getIpAddress())
                .port(request.getPort())
                .state(AgentState.PENDING_APPROVAL)
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();

        Agent saved = agentRepository.save(agent);
        log.info("New agent registered: {} awaiting admin approval.", saved.getId());

        return new AgentRegistrationResponse(saved.getId(), saved.getState());
    }

    public Agent saveAgent(Agent agent){

        return agentRepository.save(agent);
    }

    public Optional<Agent> getByIpAddressAndPort(String ipAddress, int port){
        return agentRepository.findByIpAddressAndPort(ipAddress,port);
    }



    /**
     * This update is received from agent itself
     * Ip-port changes and online status
     */
    @Transactional
    public AgentStatusResponse updateAgentStatus(AgentStatusRequest request) {

        System.out.println("Update agent requested:");

        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new AgentNotFoundException("Unknown agent: " + request.getAgentId()));

        boolean addressChanged = !request.getIpAddress().equals(agent.getIpAddress())
                || request.getPort() != agent.getPort();

        if (addressChanged) {
            agent.setIpAddress(request.getIpAddress());
            agent.setPort(request.getPort());
        }

        agent.setLastSeenAt(Instant.now());

        Agent savedagent = agentRepository.save(agent);

        System.out.println("Saved agent ");

        if (addressChanged && agent.getState() == AgentState.APPROVED) {
            peerListService.incrementVersion();
            log.info("Agent {} address changed, peer list version incremented.", request.getAgentId());
        }

        boolean peerOutdated = peerListService.isPeerListOutdated(request.getPeerVersion());

        List<AgentDTO> peers = null;

        if (peerOutdated && agent.getState() == AgentState.APPROVED) {

            peers = agentRepository.findByState(AgentState.APPROVED).stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
        }

        return AgentStatusResponse.builder()
                .status(agent.getState().name())
                .peerOutdated(peerOutdated)
                .agents(peers)
                .build();
    }

    @Transactional
    public void deleteAgent(UUID agentId) {

        Agent agent = findByAgentId(agentId);

        agentRepository.delete(agent);

        peerListService.incrementVersion();

        log.info("Agent deleted: {}", agentId);
    }

    public Agent findByAgentId(UUID agentId) {

        return agentRepository.findById(agentId).orElseThrow(() -> new AgentNotFoundException("Unknown agent: " + agentId));
    }

    public List<Agent> getAllAgents() {

        return agentRepository.findAll();
    }

    public List<Agent> getAllApproved() {

        return getAgentsByState(AgentState.APPROVED);
    }

    public List<Agent> getAllPending() {

        return getAgentsByState(AgentState.PENDING_APPROVAL);
    }

    public List<Agent> getAgentsByState(AgentState state) {

        return agentRepository.findByState(state);
    }


    public List<Agent> getOnlineAgentsBefore(Instant threshold) {

        return agentRepository.findByLastSeenAtBefore(threshold);
    }

    public List<Agent> getOnlineAgents() {
        Instant threshold = Instant.now().minus(Duration.ofMinutes(OFFLINE_THRESHOLD_MINUTES));
        return agentRepository.findByLastSeenAtAfter(threshold);
    }

    public AgentStats getAgentStats() {

        long total = agentRepository.count();

        Instant threshold = Instant.now().minus(Duration.ofMinutes(2));

        long online =
                agentRepository.countByLastSeenAtAfter(threshold);

        long pending =
                agentRepository.countByState(AgentState.PENDING_APPROVAL);

        long pendingRename =
                agentRepository.countByStateAndRequestedNameIsNotNull(AgentState.APPROVED);

        long offline = total - online;

        return new AgentStats(
                total,
                online,
                offline,
                pending,
                pendingRename
        );
    }

    public List<Agent> getPendingRenames() {

        return agentRepository.findByStateAndRequestedNameIsNotNull(AgentState.APPROVED);
    }

    /**
     * Agent network approval/rejection
     * Admin actions
     */
    @Transactional
    public void approveAgent(UUID agentId) {

        Agent agent = findByAgentId(agentId);

        if (agent.getState() != AgentState.PENDING_APPROVAL) {
            throw new IllegalStateException("Agent is not pending approval");
        }

        agent.setState(AgentState.APPROVED);
        agent.setAuthToken(agentTokenService.generateAuthToken(agent.getId(), agent.getName()));
        agent.setPublicToken(agentTokenService.generatePublicToken(agent.getId(), agent.getName()));

        Agent savedAgent = agentRepository.save(agent);

        peerListService.incrementVersion();

        agentPushService.pushApproval(savedAgent);

        log.info("Agent approved: {}", savedAgent.getId());
    }

    @Transactional
    public void rejectAgent(UUID agentId) {

        Agent agent = findByAgentId(agentId);

        if (agent.getState() != AgentState.PENDING_APPROVAL) {
            throw new IllegalStateException("Agent is not pending approval");
        }

        agentRepository.delete(agent);

        peerListService.incrementVersion();

        log.info("Agent rejected and removed: {}", agentId);
    }

    /**
     * Agent rename approval/rejection
     * Admin actions
     */
    @Transactional
    public void requestRename(UUID agentId, String newName) {

        Agent agent = findByAgentId(agentId);

        if (agent.getState() != AgentState.APPROVED) {
            throw new IllegalStateException("Only approved agents can request rename");
        }

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Invalid name");
        }

        if (agentRepository.existsByName(newName)) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        agent.setRequestedName(newName);

        agentRepository.save(agent);

        log.info("Rename requested: {} -> {}", agent.getName(), newName);
    }

    @Transactional
    public void approveRename(UUID agentId) {

        Agent agent = findByAgentId(agentId);

        if (agent.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        if (agentRepository.existsByName(agent.getRequestedName())) {
            throw new NameAlreadyInUseException("Name already in use");
        }

        agent.setName(agent.getRequestedName());

        agent.setRequestedName(null);

        agentRepository.save(agent);

        peerListService.incrementVersion();

        log.info("Rename approved for agent {}", agentId);
    }

    @Transactional
    public void rejectRename(UUID agentId) {

        Agent agent = findByAgentId(agentId);

        if (agent.getRequestedName() == null) {
            throw new IllegalStateException("No pending rename request");
        }

        agent.setRequestedName(null);

        agentRepository.save(agent);

        log.info("Rename rejected for agent {}", agentId);
    }

    public AgentDTO toDTO(Agent agent) {

        Instant now = Instant.now();
        Instant threshold = now.minus(Duration.ofMinutes(2));
        Instant lastSeen = agent.getLastSeenAt();
        boolean agentonline = agent.isOnline();

        boolean online = false;
        long diffSeconds = -1;

        if (lastSeen != null) {
            diffSeconds = Duration.between(lastSeen, now).getSeconds();
            online = lastSeen.isAfter(threshold);
        }

        System.out.println("----- AGENT DEBUG -----");
        System.out.println("Agent ID: " + agent.getId());
        System.out.println("Now: " + now);
        System.out.println("LastSeenAt: " + lastSeen);
        System.out.println("Threshold: " + threshold);
        System.out.println("Seconds since lastSeen: " + diffSeconds);
        System.out.println("Computed ONLINE: " + online);
        System.out.println("Agent side online check: " + agentonline);
        System.out.println("-----------------------");

        return AgentDTO.builder()
                .id(agent.getId())
                .agentName(agent.getName())
                .ipAddress(agent.getIpAddress())
                .port(agent.getPort())
                .online(agent.isOnline())
                .publicToken(agent.getPublicToken())
                .build();
    }

}