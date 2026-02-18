package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.repository.DataStore;
import com.atamanahmet.beamlink.nexus.service.RegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Provides peer list to agents
 */
@RestController
@RequestMapping("/api/peers")
@RequiredArgsConstructor
public class PeerController {

    private final RegistryService registryService;
    private final DataStore dataStore;

    /**
     * Get list of online agents (excluding requesting agent)
     */
    @GetMapping("/online")
    public ResponseEntity<List<Map<String, Object>>> getOnlinePeers(
            @RequestParam(required = false) String excludeAgentId) {

        List<Map<String, Object>> peers = registryService.getAllAgents().stream()
                .filter(AgentRegistry::isOnline)
                .filter(agent -> excludeAgentId == null || !agent.getAgentId().equals(excludeAgentId))  // Don't show self
                .map(agent -> {
                    Map<String, Object> peer = new HashMap<>();
                    peer.put("id", agent.getAgentId());
                    peer.put("name", agent.getName());
                    peer.put("address", agent.getAddress());
                    peer.put("online", agent.isOnline());
                    return peer;
                })
                .collect(Collectors.toList());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

    /**
     * Get list of all agents (excluding requesting agent)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPeers(
            @RequestParam(required = false) String excludeAgentId) {

        List<AgentRegistry> allAgents = dataStore.getAllAgents();

        // Filter out the requesting agent if specified
        if (excludeAgentId != null) {
            allAgents = allAgents.stream()
                    .filter(a -> !a.getAgentId().equals(excludeAgentId))
                    .toList();
        }

        Map<String, Object> response = new HashMap<>();

        response.put("peers", allAgents);
        response.put("version", dataStore.getPeerListVersion());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);

    }

    /**
     * Get specific agent address
     */
    @GetMapping("/{agentId}/address")
    public ResponseEntity<Map<String, Object>> getAgentAddress(@PathVariable String agentId) {
        AgentRegistry agent = registryService.getAllAgents().stream()
                .filter(a -> a.getAgentId().equals(agentId))
                .findFirst()
                .orElse(null);

        if (agent == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();

        response.put("ip", agent.getIpAddress());
        response.put("port", agent.getPort());
        response.put("address", agent.getAddress());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}