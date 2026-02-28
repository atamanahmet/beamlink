package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approval")
@RequiredArgsConstructor
public class ApprovalController {

    private final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final AgentService agentService;

    /**
     * Nexus calls this endpoint when an admin approves the agent
     * No polling, nexus knows agent IP and port from registration
     */
    @PostMapping
    public ResponseEntity<String> receiveApproval(@RequestBody ApprovalPushRequest request) {
        if (request.getAuthToken() == null || request.getPublicToken() == null) {
            log.warn("Received approval push with missing tokens. Rejecting.");
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Missing tokens");
        }

        agentService.applyNexusIdentity(request);

        return ResponseEntity.ok("Approved");
    }
}