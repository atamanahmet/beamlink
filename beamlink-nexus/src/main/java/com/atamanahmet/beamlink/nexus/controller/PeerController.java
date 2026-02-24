package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Provides peer list to agents
 */
@RestController
@RequestMapping("/api/nexus/peers")
@RequiredArgsConstructor
public class PeerController {

    private final PeerListService peerListService;
    private final AgentService agentService;
    private final NexusConfig nexusConfig;

    private final AgentTokenService agentTokenService;

    private static final int OFFLINE_THRESHOLD_MINUTES = 2;

    /**
     * Get list of online agents (excluding requesting agent)
     */
    @GetMapping("/online")
    public ResponseEntity<List<AgentDTO>> getOnlinePeers(
            @RequestParam(required = false) UUID excludeAgentId,
            @RequestHeader("X-Auth-Token") String token) {

        UUID agentId = agentTokenService.extractAgentId(token);

        List<AgentDTO> peers = agentService.getOnlineAgents().stream()
                .filter(agent -> !agent.getId().equals(excludeAgentId))  // Don't show self
                .map(agentService::toDTO)
                .collect(Collectors.toList());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

//    /**
//     * Get list of all agents (excluding requesting agent)
//     */
//    @GetMapping
//    public ResponseEntity<Map<String, Object>> getAllPeers(
//            @RequestParam(required = false) UUID excludeAgentId) {
//
//        List<AgentDTO> allAgents = agentService.getAllAgents().stream()
//                .filter(agent -> !agent.getId().equals(excludeAgentId))  // Don't show self
//                .map(agentService::toDTO)
//                .collect(Collectors.toList());
//
//        Map<String, Object> response = new HashMap<>();
//
//        response.put("peers", allAgents);
//        response.put("version", peerListService.getCurrentVersion());
//
//        return ResponseEntity
//                .status(HttpStatus.OK)
//                .body(response);
//    }

    /**
     * Get list of all agents (excluding requesting agent)
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllPeers(
            @RequestParam(required = false) UUID excludeAgentId) {

        List<AgentDTO> allAgents = agentService.getAllAgents().stream()
                .filter(agent -> !agent.getId().equals(excludeAgentId))
                .map(agentService::toDTO)
                .collect(Collectors.toList());

        //TODO: refactor public token creation for nexus

        UUID nexusId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        String nexusName = "Nexus";

        String nexusPublicToken = agentTokenService.generatePublicToken(nexusId, nexusName);

        AgentDTO nexusPeer = AgentDTO.builder()
                .id(nexusId)
                .agentName(nexusName)
//                .ipAddress(nexusConfig.getIpAddress())
                .ipAddress("192.168.1.86")
                .port(nexusConfig.getNexusPort())
                .publicToken(nexusPublicToken)
                .online(true)
                .build();

        allAgents.add(0, nexusPeer); // put Nexus first in list

        Map<String, Object> response = new HashMap<>();

        response.put("peers", allAgents);
        response.put("version", peerListService.getCurrentVersion());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * Get specific agent address
     */
    @GetMapping("/{agentId}/address")
    public ResponseEntity<Map<String, Object>> getAgentAddress(@PathVariable UUID agentId) {

        Agent agent = agentService.findByAgentId(agentId);


        Map<String, Object> response = new HashMap<>();

        response.put("ip", agent.getIpAddress());
        response.put("port", agent.getPort());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, Long>> getPeerListVersion() {

        Map<String, Long> response = new HashMap<>();

        response.put("version", peerListService.getCurrentVersion());


        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}