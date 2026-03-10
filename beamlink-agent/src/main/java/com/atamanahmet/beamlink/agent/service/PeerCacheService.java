package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
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
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PeerCacheService {

    private final Logger log = LoggerFactory.getLogger(PeerCacheService.class);

    private final AgentConfig config;
    private final ObjectMapper objectMapper;
    private final WebClient nexusWebClient;

    private static final String CACHE_FILE = "peers_cache.json";
    private volatile boolean initialPeersReceived = false;

    @Getter
    private long currentPeerListVersion = 0L;

    private List<Peer> cachedPeers = new ArrayList<>();

    public List<Peer> getAllPeers(UUID agentId, String publicToken) {
        if (cachedPeers.isEmpty()) refreshPeersFromNexus(agentId, publicToken);
        if (cachedPeers.isEmpty()) loadFromCache();
        return new ArrayList<>(cachedPeers);
    }

    public List<Peer> getOnlinePeers(UUID agentId, String publicToken) {
        return getAllPeers(agentId, publicToken).stream()
                .filter(Peer::isOnline)
                .toList();
    }

    public void refreshPeersFromNexus(UUID agentId, String publicToken) {
        if (initialPeersReceived) {
            log.debug("WS peer list already active. Skipping HTTP refresh.");
            return;
        }

        if (agentId == null) {
            log.info("No agent ID yet. Skipping peer refresh.");
            loadFromCache();
            return;
        }

        if (publicToken == null || publicToken.isBlank()) {
            log.warn("No public token available. Skipping peer refresh.");
            loadFromCache();
            return;
        }

        try {
            PeerListResponse response = nexusWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/nexus/peers")
                            .queryParam("excludeAgentId", agentId)
                            .build())
                    .header("X-Auth-Token", publicToken)
                    .retrieve()
                    .bodyToMono(PeerListResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        int status = ex.getStatusCode().value();
                        if (status == 401 || status == 403) {

                            log.warn("Peer fetch rejected [{}], token may not be active yet. " +
                                    "only WS peer update for now.", status);
                        } else {
                            log.error("Peer fetch failed [{}]: {}", status, ex.getMessage());
                        }
                        return Mono.empty();
                    })
                    .onErrorResume(e -> {
                        log.error("Peer fetch failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
                        return Mono.empty();
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .block();

            if (response == null) {
                log.debug("No peer response received. Loading from local cache.");
                loadFromCache();
                return;
            }

            cachedPeers = response.getPeers() != null ? response.getPeers() : new ArrayList<>();
            currentPeerListVersion = response.getVersion();
            saveToCache();

            log.info("Refreshed peer list: {} peers (version: {})",
                    cachedPeers.size(), currentPeerListVersion);

        } catch (Exception e) {
            log.error("Could not refresh from nexus: {}", e.getMessage());
            log.info("Using cached peer list: {} peers", cachedPeers.size());
        }
    }

    public void updatePeers(List<Peer> peers, long version) {
        cachedPeers = new ArrayList<>(peers);
        currentPeerListVersion = version;
        initialPeersReceived = true;
        saveToCache();
        log.info("Peer list updated via WS: {} peers (version: {})",
                cachedPeers.size(), currentPeerListVersion);
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
            log.info("Loaded {} peers from cache (version: {})",
                    cachedPeers.size(), currentPeerListVersion);
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