package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.security.DynamicCorsRegistry;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/agents")
@RequiredArgsConstructor
public class NexusAgentController {

    private final AgentService agentService;
    private final DynamicCorsRegistry corsRegistry;

    @GetMapping
    public ResponseEntity<List<Agent>> getAll() {

        List<Agent> allAgents = agentService.getAllAgents();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allAgents);
    }

    @GetMapping("/approved")
    public ResponseEntity<List<Agent>> getAllApproved() {

        List<Agent> allApproved = agentService.getAllApproved();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allApproved);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Agent>> getPending() {
        List<Agent> allPending = agentService.getAllPending();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allPending);
    }

    @GetMapping("/rename-pending")
    public ResponseEntity<List<Agent>> getRenamePendings() {

        List<Agent> renamePendings = agentService.getPendingRenames();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(renamePendings);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<HttpStatus> approve(@PathVariable UUID id) {

        agentService.approveAgent(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<HttpStatus> reject(@PathVariable UUID id) {

        agentService.rejectAgent(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/rename/approve")
    public ResponseEntity<HttpStatus> approveRename(@PathVariable UUID id) {

        agentService.approveRename(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/{id}/rename/reject")
    public ResponseEntity<HttpStatus> rejectRename(@PathVariable UUID id) {

        agentService.rejectRename(id);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<HttpStatus> delete(@PathVariable UUID id) {

        Agent agent = agentService.findByAgentId(id);

        corsRegistry.unregister("http://" + agent.getIpAddress() + ":" + agent.getPort());

        agentService.deleteAgent(id);

        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .build();
    }
    
    
}