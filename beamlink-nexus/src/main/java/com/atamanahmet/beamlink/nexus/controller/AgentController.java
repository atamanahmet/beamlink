package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.*;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.security.DynamicCorsRegistry;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Agent facing endpoints
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentService agentService;
    private final DynamicCorsRegistry corsRegistry;

    /**
     * Agent registration
     */
    @PostMapping("/register")
    public ResponseEntity<AgentRegistrationResponse> register(
            @RequestBody AgentRegistrationRequest request) {

        log.info("Agent register request: {}:{}",
                request.getIpAddress(),
                request.getPort());

        corsRegistry.register("http://" + request.getIpAddress() + ":" + request.getPort());

        AgentRegistrationResponse response = agentService.register(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Agent heartbeat / status update
     */
    @PostMapping("/status")
    public ResponseEntity<AgentStatusResponse> updateStatus(
            @RequestBody AgentStatusRequest request) {

        AgentStatusResponse response = agentService.updateAgentStatus(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    /**
     * Agent rename request
     */
    @PostMapping("/{id}/rename")
    public ResponseEntity<Void> requestRename(
            @PathVariable UUID id,
            @RequestParam String name) {

        agentService.requestRename(id, name);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Placeholder fix.
     * In case of db reset sends response to reregister
     */
    @GetMapping("/{id}/exists")
    public ResponseEntity<Void> exists(@PathVariable UUID id) {
        agentService.findByAgentId(id); // throws AgentNotFoundException if missing
        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    // AgentController
    @GetMapping("/identify")
    public ResponseEntity<AgentIdentityResponse> identify(
            @RequestParam String ipAddress,
            @RequestParam int port) {

        return agentService.getByIpAddressAndPort(ipAddress, port)
                .map(agent -> ResponseEntity.ok(AgentIdentityResponse.builder()
                        .agentId(agent.getId())
                        .agentName(agent.getName())
                        .authToken(agent.getAuthToken())
                        .publicToken(agent.getPublicToken())
                        .state(agent.getState())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }
}