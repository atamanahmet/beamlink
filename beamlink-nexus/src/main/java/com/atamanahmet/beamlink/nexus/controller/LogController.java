package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/logs")
@RequiredArgsConstructor
public class LogController {

    private final Logger log = LoggerFactory.getLogger(LogController.class);

    private final AgentService agentService;
    private final TransferLogService transferLogService;

    /**
     * Agent side, authenticated
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncLogs(
            @RequestHeader("X-Auth-Token") String authToken,
            @RequestHeader("X-Agent-Id") UUID agentId,
            @RequestBody List<LogSyncRequest> incomingLogs) {

        Agent agent = agentService.findByAgentId(agentId);

        if (!authToken.equals(agent.getAuthToken())
                || agent.getState() != AgentState.APPROVED) {
            return ResponseEntity.status(401).build();
        }

        if (incomingLogs == null || incomingLogs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No logs provided"));
        }

        List<UUID> mergedIds = transferLogService.sync(agentId, incomingLogs);

        log.info("Synced {} logs from agent {}", mergedIds.size(), agentId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "mergedLogIds", mergedIds
        ));
    }

    /**
     * Admin-facing — paginated.
     */
    @GetMapping
    public ResponseEntity<List<TransferLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(transferLogService.getLogs(PageRequest.of(page, size)));
    }

    /**
     * Admin-facing — convenience shortcut for recent logs.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TransferLog>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(transferLogService.getRecentLogs(limit));
    }
}