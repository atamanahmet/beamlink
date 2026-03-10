package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationRequest;
import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.nexus.dto.AgentRenameRequest;
import com.atamanahmet.beamlink.nexus.dto.AgentStatusRequest;
import com.atamanahmet.beamlink.nexus.dto.AgentStatusResponse;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.security.DynamicCorsRegistry;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

        private final AgentService agentService;
        private final DynamicCorsRegistry corsRegistry;
        private final AgentTokenService agentTokenService;

        /**
         * Agent registration
         */
        @PostMapping("/register")
        public ResponseEntity<AgentRegistrationResponse> register(
                @RequestBody AgentRegistrationRequest request) {

                log.info("Agent register request: {}:{}", request.getIpAddress(), request.getPort());

                corsRegistry.register("http://" + request.getIpAddress() + ":" + request.getPort());

                return ResponseEntity.ok(agentService.register(request));
        }

        /**
         * Agent heartbeat / status update
         */
        @PostMapping("/status")
        public ResponseEntity<AgentStatusResponse> updateStatus(
                @RequestBody AgentStatusRequest request) {

                return ResponseEntity.ok(agentService.updateAgentStatus(request));
        }

        /**
         * Agent rename request
         */
        @PostMapping("/{id}/rename")
        public ResponseEntity<Void> requestRename(
                @PathVariable UUID id,
                @RequestBody AgentRenameRequest request) {

                agentService.requestRename(id, request.getName());
                return ResponseEntity.ok().build();
        }

        /**
         * Checks if agent still exists in DB.
         * Used by agent on startup to detect DB resets.
         */
        @GetMapping("/{id}/exists")
        public ResponseEntity<Void> exists(@PathVariable UUID id) {
                agentService.findByAgentId(id);
                return ResponseEntity.ok().build();
        }

        @GetMapping("/ping")
        public ResponseEntity<Void> ping() {
                return ResponseEntity.ok().build();
        }

        /**
         * Agent identity lookup by ip:port.
         * Returns id, name, state, and fresh tokens if approved.
         */
        @GetMapping("/identify")
        public ResponseEntity<AgentIdentityResponse> identify(
                @RequestParam String ipAddress,
                @RequestParam int port) {

                return agentService.getByIpAddressAndPort(ipAddress, port)
                        .map(agent -> ResponseEntity.ok(AgentIdentityResponse.builder()
                                .agentId(agent.getId())
                                .agentName(agent.getName())
                                .state(agent.getState())
                                .authToken(agent.getState() == AgentState.APPROVED
                                        ? agentTokenService.generateAuthToken(agent.getId())
                                                : null)
                                .publicToken(
                                        agent.getState() == AgentState.APPROVED
                                                && agent.getPublicId() != null
                                        ? agentTokenService.generatePublicToken(agent.getId(), agent.getPublicId()) : null)
                                .build()))
                        .orElse(ResponseEntity.notFound().build());
        }
}