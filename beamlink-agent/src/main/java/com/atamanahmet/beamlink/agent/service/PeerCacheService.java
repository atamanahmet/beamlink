package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.domain.PeerCache;
import com.atamanahmet.beamlink.agent.dto.PeerListResponse;
import com.atamanahmet.beamlink.agent.dto.PeerStatusUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PeerCacheService {

    private final Logger log = LoggerFactory.getLogger(PeerCacheService.class);

    private final AgentConfig config;
    private final ObjectMapper objectMapper;
    private final WebClient nexusWebClient;

    private static final String CACHE_FILE = "peers_cache.json";

    @Getter
    private long currentPeerListVersion = 0L;

    private List<Peer> cachedPeers = new ArrayList<>();

    public List<Peer> getAllPeers(Agent agent) {
        if (cachedPeers.isEmpty()) refreshPeersFromNexus(agent);
        if (cachedPeers.isEmpty()) loadFromCache();
        return new ArrayList<>(cachedPeers);
    }

    public void refreshPeersFromNexus(Agent agent) {
        if (!agent.isApproved()) {
            log.info("Agent not approved yet. Skipping peer refresh.");
            loadFromCache();
            return;
        }

        try {
            PeerListResponse response = nexusWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/nexus/peers")
                            .queryParam("excludeAgentId", agent.getId())
                            .build())
                    .header("X-Auth-Token", agent.getPublicToken())
                    .retrieve()
                    .bodyToMono(PeerListResponse.class)
                    .onErrorResume(e -> {
                        log.error("Peer fetch failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (response == null) {
                log.warn("Cannot fetch peers: Nexus offline or agent pending approval");
                loadFromCache();
                return;
            }

            cachedPeers = response.getPeers() != null ? response.getPeers() : new ArrayList<>();
            currentPeerListVersion = response.getVersion();
            saveToCache();

            log.info("Refreshed peer list: {} peers (version: {})", cachedPeers.size(), currentPeerListVersion);

        } catch (Exception e) {
            log.error("Could not refresh from nexus: {}", e.getMessage());
            log.info("Using cached peer list: {} peers", cachedPeers.size());
        }
    }

    public void updatePeerStatuses(List<PeerStatusUpdate> agentStatuses) {
        if (agentStatuses == null || agentStatuses.isEmpty()) return;

        for (PeerStatusUpdate status : agentStatuses) {
            cachedPeers.stream()
                    .filter(p -> p.getAgentId().toString().equals(status.getAgentId()))
                    .findFirst()
                    .ifPresent(p -> p.setOnline(status.isOnline()));
        }
    }

    public void updatePeers(List<Peer> peers, long version) {
        cachedPeers = new ArrayList<>(peers);
        currentPeerListVersion = version;
        saveToCache();
        log.info("Peer list updated via WS: {} peers (version: {})", cachedPeers.size(), currentPeerListVersion);
    }

    public List<Peer> getOnlinePeers(Agent agent) {
        return getAllPeers(agent).stream()
                .filter(Peer::isOnline)
                .toList();
    }

    public void clearCache() {
        cachedPeers.clear();
        log.info("Peer cache cleared");
    }

    private void loadFromCache() {
        File cacheFile = new File(CACHE_FILE);
        if (!cacheFile.exists()) return;

        try {
            PeerCache cache = objectMapper.readValue(cacheFile, PeerCache.class);
            cachedPeers = cache.getPeers() != null ? cache.getPeers() : new ArrayList<>();
            currentPeerListVersion = cache.getVersion();
            log.info("Loaded {} peers from cache (version: {})", cachedPeers.size(), currentPeerListVersion);
        } catch (IOException e) {
            log.error("Error loading peer cache: {}", e.getMessage());
            cachedPeers = new ArrayList<>();
            currentPeerListVersion = 0;
        }
    }

    private void saveToCache() {
        try {
            PeerCache cache = new PeerCache();
            cache.setPeers(cachedPeers);
            cache.setVersion(currentPeerListVersion);
            objectMapper.writeValue(new File(CACHE_FILE), cache);
        } catch (IOException e) {
            log.error("Error saving peer cache: {}", e.getMessage());
        }
    }
}