package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nexus/peers")
@RequiredArgsConstructor
public class PeerController {

        private final PeerListService peerListService;
        private final AgentService agentService;
        private final NexusConfig nexusConfig;

        private static final UUID NEXUS_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
        private static final UUID NEXUS_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

        /**
         * Get list of online agents (excluding requesting agent)
         */
        @GetMapping("/online")
        public ResponseEntity<List<AgentDTO>> getOnlinePeers(
                @RequestParam(required = false) UUID excludeAgentId) {

                List<AgentDTO> peers = agentService.getOnlineAgents().stream()
                        .filter(agent -> !agent.getId().equals(excludeAgentId))
                        .map(agentService::toDTO)
                        .collect(Collectors.toList());

                return ResponseEntity.ok(peers);
        }

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

                allAgents.add(0, buildNexusPeer());

                Map<String, Object> response = new HashMap<>();
                response.put("peers", allAgents);
                response.put("version", peerListService.getCurrentVersion());

                return ResponseEntity.ok(response);
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

                return ResponseEntity.ok(response);
        }

        @GetMapping("/version")
        public ResponseEntity<Map<String, Long>> getPeerListVersion() {
                return ResponseEntity.ok(Map.of("version", peerListService.getCurrentVersion()));
        }

        private AgentDTO buildNexusPeer() {
                return AgentDTO.builder()
                        .id(NEXUS_ID)
                        .agentName(nexusConfig.getName())
                        .ipAddress(nexusConfig.getIpAddress())
                        .port(nexusConfig.getNexusPort())
                        .publicId(NEXUS_PUBLIC_ID)
                        .online(true)
                        .build();
        }
}