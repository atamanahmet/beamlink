package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.FileTransferService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileTransferService fileTransferService;
    private final AgentTokenService agentTokenService;
    private final AgentService agentService;

    /**
     * Check if there's issues before uploading, preflight
     * Extract identity from verified token
     */
    @GetMapping("/upload/check")
    public ResponseEntity<Map<String, Object>> checkDiskSpace(
            @RequestParam("fileSize") long fileSize,
            @RequestParam("filename") String filename,
            @RequestHeader("X-Auth-Token") String token) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID agentId = agentTokenService.extractAgentId(token);
            Agent agent = agentService.findByAgentId(agentId);
            if (agent.getState() != AgentState.APPROVED) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .body(response);
            }
            if (filename == null || filename.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Invalid filename");
                response.put("message", "Filename cannot be empty");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                response.put("success", false);
                response.put("error", "Invalid filename");
                response.put("message", "Filename contains invalid characters");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            boolean hasSpace = fileTransferService.checkDiskSpaceAvailable(fileSize);

            if (hasSpace) {
                response.put("success", true);
                response.put("message", "Ready to receive file");

                return ResponseEntity
                        .status(HttpStatus.OK)
                        .body(response);

            } else {
                response.put("success", false);
                response.put("error", "Insufficient disk space");
                response.put("message", "Not enough disk space for this file");

                return ResponseEntity
                        .status(HttpStatus.INSUFFICIENT_STORAGE)
                        .body(response);
            }

        } catch (AgentNotFoundException e) {

            // Agent sends new registraion
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .build();
        }
    }

    /**
     * Upload endpoint - receives files from agents
     * Extract identity from verified token
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-Auth-Token") String token) throws Exception {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID agentId = agentTokenService.extractAgentId(token);
            Agent agent = agentService.findByAgentId(agentId);
            if (agent.getState() != AgentState.APPROVED) {
                return ResponseEntity
                        .status(HttpStatus.FORBIDDEN)
                        .build();
            }
            if (file == null || file.isEmpty()) {
                response.put("success", false);
                response.put("error", "No file provided");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            String filename = file.getOriginalFilename();

            if (filename == null || filename.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "Invalid filename");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                response.put("success", false);
                response.put("error", "Invalid filename");
                response.put("message", "Filename contains invalid characters");

                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            long bytesWritten = fileTransferService.receiveFileStream(
                    file.getInputStream(),
                    filename,
                    file.getSize(),
                    agentId,
                    agent.getName()
            );

            response.put("success", true);
            response.put("filename", filename);
            response.put("size", bytesWritten);

            return ResponseEntity
                    .status(HttpStatus.OK)
                    .body(response);

        } catch (AgentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }


    }

    /**
     * Ping endpoint
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {

        Map<String, Object> response = new HashMap<>();
        response.put("status", "online");
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }
}