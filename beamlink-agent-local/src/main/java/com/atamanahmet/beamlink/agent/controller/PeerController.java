package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.service.PeerCacheService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import lombok.RequiredArgsConstructor;

/**
 * Provides peer list for UI
 */
@RestController
@RequestMapping("/api/peers")
@RequiredArgsConstructor
public class PeerController {

    private final PeerCacheService peerCacheService;

    /**
     * Get list of all peers
     */
    @GetMapping
    public ResponseEntity<List<Peer>> getAllPeers() {

        List<Peer> peers = peerCacheService.getAllPeers();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

    /**
     * Get list of available peers
     */
    @GetMapping("/online")
    public ResponseEntity<List<Peer>> getPeers() {

        List<Peer> peers = peerCacheService.getOnlinePeers();
        // All peers
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }

    /**
     * Refresh peer list from admin
     */
    @PostMapping("/refresh")
    public ResponseEntity<List<Peer>> refreshPeers() {

        peerCacheService.refreshPeersFromNexus();

        List<Peer> peers = peerCacheService.getAllPeers();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(peers);
    }
}