package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.service.UpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/update")
@RequiredArgsConstructor
public class NexusUpdateController {

    private final UpdateService updateService;

    @PostMapping("/upload")
    public ResponseEntity<Void> upload(@RequestParam("file") MultipartFile file) {
        updateService.storePackage(file);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/push/{agentId}")
    public ResponseEntity<Void> pushToOne(@PathVariable UUID agentId) {
        updateService.pushToAgent(agentId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/push-all")
    public ResponseEntity<Void> pushToAll() {
        updateService.pushToAllOnline();
        return ResponseEntity.ok().build();
    }
}