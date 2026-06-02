package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.dto.AgentRenameRequest;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/agent")
@RequiredArgsConstructor
public class AgentEndpointController {

    private final AgentService agentService;

    @PostMapping("/{id}/rename")
    public ResponseEntity<Void> requestRename(
            @PathVariable UUID id,
            @RequestBody AgentRenameRequest request) {
        agentService.requestRename(id, request.getName());
        return ResponseEntity.ok().build();
    }
}