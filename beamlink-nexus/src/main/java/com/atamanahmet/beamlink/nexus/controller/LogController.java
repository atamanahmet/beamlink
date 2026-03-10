package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/nexus/logs")
@RequiredArgsConstructor
public class LogController {

    private final TransferLogService transferLogService;

    /**
     * Agent side, authenticated via filter, agentId extracted from security context
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncLogs(
            Authentication authentication,
            @RequestBody List<LogSyncRequest> incomingLogs) {

        if (incomingLogs == null || incomingLogs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No logs provided"));
        }

        UUID agentId = (UUID) authentication.getPrincipal();
        List<UUID> mergedIds = transferLogService.sync(agentId, incomingLogs);

        log.info("Synced {} logs from agent {}", mergedIds.size(), agentId);

        return ResponseEntity.ok(Map.of("success", true, "mergedLogIds", mergedIds));
    }

    /**
     * Admin-facing
     */
    @GetMapping
    public ResponseEntity<List<TransferLog>> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(transferLogService.getLogs(PageRequest.of(page, size)));
    }

    /**
     * Admin-facing, recent logs
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TransferLog>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity.ok(transferLogService.getRecentLogs(limit));
    }
}