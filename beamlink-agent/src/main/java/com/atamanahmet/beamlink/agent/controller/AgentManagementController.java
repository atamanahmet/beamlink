package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.AgentRenameRequest;
import com.atamanahmet.beamlink.agent.dto.AgentRenameResponse;
import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentManagementController {

    private final AgentService agentService;
    private final WebClient nexusWebClient;

    @PostMapping("/{id}/rename")
    public ResponseEntity<Void> requestRename(
            @PathVariable UUID id,
            @RequestBody AgentRenameRequest request) {

        nexusWebClient.post()
                .uri("/api/agents/" + id + "/rename")
                .header("X-Auth-Token", agentService.getAuthToken())
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        return ResponseEntity.ok().build();
    }

    @PostMapping("/rename")
    public ResponseEntity<Void> receiveRename(@RequestBody AgentRenameResponse payload) {
        if (payload.getAgentName() != null && !payload.getAgentName().isBlank()) {
            agentService.updateAgentName(payload.getAgentName());
        }
        return ResponseEntity.ok().build();
    }
}