package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.domain.PendingAgent;
import com.atamanahmet.beamlink.nexus.domain.PendingRename;
import com.atamanahmet.beamlink.nexus.repository.DataStore;
import com.atamanahmet.beamlink.nexus.service.RegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages agent registration and status
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final RegistryService registryService;
    private final DataStore dataStore;

    /**
     * Register new agent (goes to pending)
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.get("agentId");
        String name = (String) request.get("name");
        String ipAddress = (String) request.get("ipAddress");
        int port = (int) request.get("port");

        registryService.registerAgent(agentId, name, ipAddress, port);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "pending_approval");
        response.put("message", "Agent registration received");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @RequestBody Map<String, Object> request) {

        String agentId = (String) request.get("agentId");
        String agentName = (String) request.get("agentName");
        String status = (String) request.get("status");

        long agentPeerVersion = request.containsKey("peerVersion")
                ? ((Number) request.get("peerVersion")).longValue()
                : 0L;

        int unsyncedLogs = request.containsKey("unsyncedLogs") ?
                (int) request.get("unsyncedLogs") : 0;

        registryService.updateAgentStatus(agentId, status, unsyncedLogs);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");

        // Check if registry name different
        AgentRegistry agent = registryService.getAgentById(agentId);

        if (agent != null && agent.getName() != null) {

            String approvedName = agent.getName();

            if (!approvedName.equals(agentName)) {

                response.put("renameApproved", true);
                response.put("newName", approvedName);
            }
        }
        // Compare peer list versions
        long currentPeerVersion = dataStore.getPeerListVersion();
        response.put("peerVersion", currentPeerVersion);
        response.put("peerOutdated", currentPeerVersion > agentPeerVersion);

        // ADD: Include lightweight agent statuses (exclude requesting agent)
        List<Map<String, Object>> agentStatuses = new ArrayList<>();

        for (AgentRegistry a : dataStore.getAllAgents()) {

            if (!a.getAgentId().equals(agentId)) { // Exclude self
                Map<String, Object> statusData = new HashMap<>();
                statusData.put("agentId", a.getAgentId());
                statusData.put("online", a.isOnline());
                statusData.put("fileCount", a.getFileCount());
                agentStatuses.add(statusData);
            }
        }
        response.put("agentStatuses", agentStatuses);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }


    /**
     * Get all registered agents
     */
    @GetMapping
    public ResponseEntity<List<AgentRegistry>> getAllAgents() {

        List<AgentRegistry> allAgents = registryService.getAllAgents();

        return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(allAgents);
    }

    /**
     * Get pending agents
     */
    @GetMapping("/pending")
    public ResponseEntity<List<PendingAgent>> getPendingAgents() {

        List<PendingAgent> pendingAgents = registryService.getPendingAgents();

        return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(pendingAgents);
    }

    /**
     * Approve pending agent
     */
    @PostMapping("/{agentId}/approve")
    public ResponseEntity<Map<String, Object>> approveAgent(@PathVariable String agentId) {
        boolean success = registryService.approveAgent(agentId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Agent approved" : "Agent not found");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Reject pending agent
     */
    @PostMapping("/{agentId}/reject")
    public ResponseEntity<Map<String, Object>> rejectAgent(@PathVariable String agentId) {
        boolean success = registryService.rejectAgent(agentId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Agent rejected" : "Agent not found");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/{agentId}/rename")
    public ResponseEntity<Map<String, Object>> requestRename(
            @PathVariable String agentId,
            @RequestBody Map<String, String> request) {

        String newName = request.get("name");

        boolean success = registryService.requestRename(agentId, newName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ?
                "Rename request submitted" :
                "Rename request failed");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/pending-renames")
    public ResponseEntity<List<PendingRename>> getPendingRenames() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(registryService.getPendingRenames());
    }

    @PostMapping("/{agentId}/approve-rename")
    public ResponseEntity<Map<String, Object>> approveRename(
            @PathVariable String agentId) {

        boolean success = registryService.approveRename(agentId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ?
                "Rename approved" :
                "Rename not found");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/{agentId}/reject-rename")
    public ResponseEntity<Map<String, Object>> rejectRename(
            @PathVariable String agentId) {

        boolean success = registryService.rejectRename(agentId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ?
                "Rename rejected" :
                "Rename not found");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/{agentId}/remove")
    public ResponseEntity<Map<String, Object>> removeAgent(@PathVariable String agentId) {

        boolean success = registryService.removeAgent(agentId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Agent removed" : "Agent not found");

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }



}