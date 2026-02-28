package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.nexus.exception.FileTransferException;
import com.atamanahmet.beamlink.nexus.exception.InsufficientDiskSpaceException;
import com.atamanahmet.beamlink.nexus.exception.NexusOfflineException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AgentAlreadyExistsException.class)
    public ResponseEntity<AgentRegistrationResponse> handleAgentAlreadyExists(AgentAlreadyExistsException ex) {

        Agent agent = ex.getAgent();

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new AgentRegistrationResponse(agent.getId(), agent.getState(), agent.getAuthToken(), agent.getPublicToken()));
    }
    @ExceptionHandler(NameAlreadyInUseException.class)
    public ResponseEntity<?> handleAgentAlreadyExists(NameAlreadyInUseException ex) {


        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("Name is already in use");
    }

    @ExceptionHandler(InsufficientDiskSpaceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientDiskSpaceException(InsufficientDiskSpaceException e) {

        log.error("Insufficient disk space: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "Insufficient disk space");
        response.put("message", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.INSUFFICIENT_STORAGE)
                .body(response);
    }

    @ExceptionHandler(FileTransferException.class)
    public ResponseEntity<Map<String, Object>> handleFileTransferException(FileTransferException e) {
        log.error("File transfer failed: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "File transfer failed");
        response.put("message", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {

        log.warn("File size exceeds maximum allowed size");

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "File too large");
        response.put("message", "File size exceeds the maximum allowed size");

        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(response);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException e) {
        log.error("IO error during file operation: {}", e.getMessage(), e);

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", "IO error");
        response.put("message", "Failed to save file: " + e.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<String> handleAgentNotFound(AgentNotFoundException e) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(e.getMessage());
    }
}
