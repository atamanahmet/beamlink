package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.domain.PendingAgent;
import com.atamanahmet.beamlink.nexus.domain.PendingRename;
import com.atamanahmet.beamlink.nexus.repository.DataStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages agent registration and approval
 */
@Service
@RequiredArgsConstructor
public class RegistryService {

    private final DataStore dataStore;
    private final Logger log = LoggerFactory.getLogger(RegistryService.class);


    /**
     * Register new agent (pending approval)
     */
    public void registerAgent(String agentId, String name, String ipAddress, int port) {

        // Check if already approved
        AgentRegistry existing = dataStore.getAgent(agentId);

        if (existing != null) {

            boolean structuralChange = !existing.getName().equals(name) ||
                            !existing.getIpAddress().equals(ipAddress) ||
                            existing.getPort() != port;

            // Update existing agent
            existing.setName(name);
            existing.setIpAddress(ipAddress);
            existing.setPort(port);
            existing.setLastSeen(LocalDateTime.now());
            existing.setOnline(true);

            // Only increment version if structure changed
            if (structuralChange) {
                dataStore.saveAgentWithVersionIncrement(existing);
                log.info("✓ Agent updated with structural changes: {}", name);
            } else {
                dataStore.saveAgent(existing);
                log.info("✓ Agent status updated: {}", name);
            }

            return;
        }

        // Check if pending
        PendingAgent pending = dataStore.getPendingAgent(agentId);
        if (pending != null) {
            log.info("Agent already pending approval: {}" , name);
            return;
        }

        // Add to pending queue
        PendingAgent newPending = new PendingAgent();
        newPending.setAgentId(agentId);
        newPending.setName(name);
        newPending.setIpAddress(ipAddress);
        newPending.setPort(port);
        newPending.setRequestedAt(LocalDateTime.now());

        dataStore.savePendingAgent(newPending);
        log.info("New agent pending approval: {} ipAddress: ({})", name, ipAddress);
    }

    public AgentRegistry getAgentById(String agentId) {
        return dataStore.getAgent(agentId);
    }

    public boolean requestRename(String agentId, String newName) {

        AgentRegistry agent = dataStore.getAgent(agentId);

        // Must exist and be approved
        if (agent == null || !agent.isApproved()) {
            return false;
        }

        if (newName == null || newName.trim().isEmpty()) {
            return false;
        }

        // If same name, ignore
        if (agent.getName().equals(newName)) {
            return false;
        }



        // Prevent duplicate approved names
        boolean nameExists = dataStore.getAllAgents().stream()
                .anyMatch(a -> a.getName().equalsIgnoreCase(newName));

        if (nameExists) {
            return false;
        }

        // Prevent duplicate pending rename names
        boolean pendingNameExists = dataStore.getAllPendingRenames().stream()
                .anyMatch(r -> r.getRequestedName().equalsIgnoreCase(newName));

        if (pendingNameExists) {
            return false;
        }

        // Only one rename per agent, before approval
        if (dataStore.getPendingRename(agentId) != null) {
            return false;
        }

        PendingRename pending = new PendingRename();
        pending.setAgentId(agentId);
        pending.setCurrentName(agent.getName());
        pending.setRequestedName(newName);
        pending.setRequestedAt(LocalDateTime.now());

        dataStore.savePendingRename(pending);

        log.info("Rename requested: {} → {}", agent.getName(), newName);

        return true;
    }

    /**
     * Approve pending agent
     */
    public boolean approveAgent(String agentId) {
        PendingAgent pending = dataStore.getPendingAgent(agentId);
        if (pending == null) {
            return false;
        }

        AgentRegistry approved = new AgentRegistry();
        approved.setAgentId(pending.getAgentId());
        approved.setName(pending.getName());
        approved.setIpAddress(pending.getIpAddress());
        approved.setPort(pending.getPort());
        approved.setOnline(true);
        approved.setApproved(true);
        approved.setLastSeen(LocalDateTime.now());
        approved.setRegisteredAt(LocalDateTime.now());
        approved.setFileCount(0);

        dataStore.saveAgentWithVersionIncrement(approved);
        dataStore.deletePendingAgent(agentId);

        log.info("✓ Agent approved: {} ", approved.getName());
        return true;
    }

    /**
     * Reject pending agent
     */
    public boolean rejectAgent(String agentId) {
        PendingAgent pending = dataStore.getPendingAgent(agentId);
        if (pending == null) {
            return false;
        }

        dataStore.deletePendingAgent(agentId);
        log.info("✗ Agent rejected: {}", pending.getName());
        return true;
    }

    public boolean approveRename(String agentId) {

        PendingRename pending = dataStore.getPendingRename(agentId);
        if (pending == null) {
            return false;
        }

        AgentRegistry agent = dataStore.getAgent(agentId);
        if (agent == null) {
            return false;
        }

        agent.setName(pending.getRequestedName());
        dataStore.saveAgentWithVersionIncrement(agent);

        dataStore.deletePendingRename(agentId);

        log.info("✓ Rename approved: {} → {}",
                pending.getCurrentName(),
                pending.getRequestedName());

        return true;
    }

    public boolean rejectRename(String agentId) {

        PendingRename pending = dataStore.getPendingRename(agentId);
        if (pending == null) {
            return false;
        }

        dataStore.deletePendingRename(agentId);

        log.info("✗ Rename rejected: {} → {}",
                pending.getCurrentName(),
                pending.getRequestedName());

        return true;
    }

    public List<PendingRename> getPendingRenames() {
        return dataStore.getAllPendingRenames();
    }

    /**
     * Update agent status
     */
    public void updateAgentStatus(String agentId, String status, int fileCount) {
        AgentRegistry agent = dataStore.getAgent(agentId);
        if (agent != null) {
            agent.setOnline("online".equals(status));
            agent.setLastSeen(LocalDateTime.now());
            agent.setFileCount(fileCount);
            dataStore.saveAgent(agent);
        }
    }

    /**
     * Remove an approved agent completely from the registry
     */
    public boolean removeAgent(String agentId) {
        AgentRegistry agent = dataStore.getAgent(agentId);
        if (agent == null) {
            log.warn("✗ Cannot remove agent – not found: {}", agentId);
            return false;
        }

        // Delete the agent
        dataStore.deleteAgent(agentId);

        // Also delete any pending rename requests
        if (dataStore.getPendingRename(agentId) != null) {
            dataStore.deletePendingRename(agentId);
        }

        log.info("Agent removed: {}", agent.getName());
        return true;
    }

    /**
     * Get all approved agents
     */
    public List<AgentRegistry> getAllAgents() {
        return dataStore.getAllAgents();
    }

    /**
     * Get all pending agents
     */
    public List<PendingAgent> getPendingAgents() {
        return dataStore.getAllPendingAgents();
    }
}