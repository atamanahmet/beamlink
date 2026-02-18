package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.domain.NexusStats;
import com.atamanahmet.beamlink.nexus.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Dashboard statistics
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final StatsService statsService;

    /**
     * Get dashboard statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<NexusStats> getStats() {

        NexusStats stats = statsService.getStats();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(stats);
    }
}