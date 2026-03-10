package com.atamanahmet.beamlink.nexus.exception;

import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentAlreadyExistsException.class)
    public ResponseEntity<AgentRegistrationResponse> handleAgentAlreadyExists(AgentAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new AgentRegistrationResponse(ex.getAgentId(), ex.getState()));
    }

    @ExceptionHandler(NameAlreadyInUseException.class)
    public ResponseEntity<String> handleNameAlreadyInUse(NameAlreadyInUseException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body("Name is already in use");
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<String> handleAgentNotFound(AgentNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InsufficientDiskSpaceException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientDiskSpace(InsufficientDiskSpaceException ex) {
        log.error("Insufficient disk space: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.INSUFFICIENT_STORAGE)
                .body(Map.of("success", false, "error", "Insufficient disk space", "message", ex.getMessage()));
    }

    @ExceptionHandler(FileTransferException.class)
    public ResponseEntity<Map<String, Object>> handleFileTransfer(FileTransferException ex) {
        log.error("File transfer failed: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "File transfer failed", "message", ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("File size exceeds maximum allowed size");
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("success", false, "error", "File too large", "message", "File size exceeds the maximum allowed size"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException ex) {
        log.error("IO error during file operation: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("success", false, "error", "IO error", "message", "Failed to save file: " + ex.getMessage()));
    }
}