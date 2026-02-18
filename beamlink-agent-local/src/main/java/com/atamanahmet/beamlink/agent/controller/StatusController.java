package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.service.AgentInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides agent status information
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatusController {

    private final AgentConfig config;
    private final AgentInfoService agentInfoService;

    /**
     * Get agent status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {

        Map<String, Object> status = new HashMap<>();

        status.put("agentId", agentInfoService.getAgentId());
        status.put("name", agentInfoService.getAgentName());
        status.put("port", config.getPort());
        status.put("uploadDirectory", config.getUploadDirectory());
        status.put("fileCount", getFileCount());
        status.put("status", "online");
        status.put("nexusUrl", config.getNexusUrl());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(status);
    }

    private int getFileCount() {
        File dir = new File(config.getUploadDirectory());
        File[] files = dir.listFiles();
        return (files != null) ? files.length : 0;
    }
}