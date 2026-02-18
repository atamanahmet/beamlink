package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.service.LogService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Transfer log management
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;
    private final Logger log = LoggerFactory.getLogger(LogController.class);

    /**
     * Get all transfer logs
     */
    @GetMapping
    public ResponseEntity<List<TransferLog>> getAllLogs() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(logService.getAllLogs());
    }

    /**
     * Get recent logs
     */
    @GetMapping("/recent")
    public ResponseEntity<List<TransferLog>> getRecentLogs(
            @RequestParam(defaultValue = "50") int limit) {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(logService.getRecentLogs(limit));
    }

    /**
     * Receive and merge logs from agents
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> syncLogs(
            @RequestBody List<TransferLog> agentLogs) {

        Map<String, Object> response = new HashMap<>();

        try {
            List<String> mergedLogIds = logService.mergeLogs(agentLogs);

            response.put("success", true);
            response.put("message", "Logs merged successfully");
            response.put("mergedCount", mergedLogIds.size());
            response.put("mergedLogIds", mergedLogIds);

            log.info("📋 Merged {} logs from agent", mergedLogIds.size());

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response);

        } catch (Exception e) {
            log.error("Failed to merge logs", e);

            response.put("success", false);
            response.put("error", "Failed to merge logs");
            response.put("message", e.getMessage());

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}
