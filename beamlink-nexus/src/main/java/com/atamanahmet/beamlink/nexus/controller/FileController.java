package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.service.FileTransferService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {

    private final Logger log = LoggerFactory.getLogger(FileController.class);
    private final FileTransferService fileTransferService;

    /**
     * Check if there's issues before uploading, preflight
     */
    @GetMapping("/upload/check")
    public ResponseEntity<Map<String, Object>> checkDiskSpace(
            @RequestParam("fileSize") long fileSize,
            @RequestParam("filename") String filename) {

        Map<String, Object> response = new HashMap<>();

        // Validate filename first
        if (filename == null || filename.trim().isEmpty()) {

            response.put("success", false);
            response.put("error", "Invalid filename");
            response.put("message", "Filename cannot be empty");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        // Check for dangerous characters in filename
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {

            response.put("success", false);
            response.put("error", "Invalid filename");
            response.put("message", "Filename contains invalid characters");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        // Check disk space
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
    }

    /**
     * Upload endpoint - receives files from agents
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fromAgent", required = false) String fromAgentId,
            @RequestParam(value = "fromName", required = false) String fromName) throws Exception {

        Map<String, Object> response = new HashMap<>();

        // Validate file exists
        if (file == null || file.isEmpty()) {

            response.put("success", false);
            response.put("error", "No file provided");
            response.put("message", "Please select a file to upload");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        String filename = file.getOriginalFilename();

        // Validate filename
        if (filename == null || filename.trim().isEmpty()) {

            response.put("success", false);
            response.put("error", "Invalid filename");
            response.put("message", "File has no name");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }

        // Stream from MultipartFile to disk
        long bytesWritten = fileTransferService.receiveFileStream(
                file.getInputStream(),
                filename,
                file.getSize(),
                fromAgentId,
                fromName
        );

        response.put("success", true);
        response.put("message", "File uploaded successfully");
        response.put("filename", filename);
        response.put("size", bytesWritten);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
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