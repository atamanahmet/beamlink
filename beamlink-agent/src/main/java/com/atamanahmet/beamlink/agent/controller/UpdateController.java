package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.service.UpdateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/update")
@RequiredArgsConstructor
@Slf4j
public class UpdateController {

    private final UpdateService updateService;

    @PostMapping("/receive")
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        try {
            byte[] bytes = request.getInputStream().readAllBytes();
            updateService.applyUpdate(bytes);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to receive update: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}