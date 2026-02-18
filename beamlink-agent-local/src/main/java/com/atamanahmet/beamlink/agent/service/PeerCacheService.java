package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.domain.PeerCache;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Manages peer list and caching
 */
@Service
@RequiredArgsConstructor
public class PeerCacheService {

    private final Logger log = LoggerFactory.getLogger(PeerCacheService.class);

    private final AgentConfig config;
    private final Gson gson = new Gson();
    private static final String CACHE_FILE = "peers_cache.json";
    private final AgentInfoService agentInfoService;

    private long currentPeerListVersion = 0L;

    public long getCurrentPeerListVersion() {
        return currentPeerListVersion;
    }


    private List<Peer> cachedPeers = new ArrayList<>();

    /**
     * Get list of peers (from cache or nexus)
     */
    public List<Peer> getAllPeers() {

        if (cachedPeers.isEmpty()) {
            // Try to fetch fresh from nexus
            refreshPeersFromNexus();
        }

        if (cachedPeers.isEmpty()) {
            // If nexus offline, try to load from cache file
            loadFromCache();
        }


        return new ArrayList<>(cachedPeers);
    }

    /**
     * Refresh peer list from nexus server (excludes this agent)
     */
    public void refreshPeersFromNexus() {
        try {
            WebClient client = WebClient.create();
            String myAgentId = agentInfoService.getAgentId();
            String url = config.getNexusUrl() + "/api/peers?excludeAgentId=" + myAgentId;

            Map<String, Object> response = client.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response == null) {
                cachedPeers = new ArrayList<>();
                return;
            }

            List<Map<String, Object>> peersData = (List<Map<String, Object>>) response.get("peers");

            Long version = ((Number) response.get("version")).longValue();

            List<Peer> fetchedPeers = new ArrayList<>();

            if (peersData != null) {
                for (Map<String, Object> data : peersData) {
                    Peer peer = new Peer();
                    peer.setAgentId((String) data.get("agentId"));
                    peer.setAgentName((String) data.get("name"));
                    peer.setIpAddress((String) data.get("ipAddress"));
                    peer.setPort(((Number) data.get("port")).intValue());
                    peer.setOnline((Boolean) data.get("online"));
                    fetchedPeers.add(peer);
                }
            }

            // Update both peers and version
            cachedPeers = fetchedPeers;
            currentPeerListVersion = version;

            saveToCache();

            log.info("Refreshed peer list from nexus: {} peers (version: {})", cachedPeers.size(), currentPeerListVersion);

        } catch (Exception e) {
            log.error("Could not refresh from nexus: {}", e.getMessage());
            log.info("Using cached peer list {} peers", cachedPeers.size());
        }
    }

    /**
     * Update peer statuses in memory (online/offline/fileCount)
     * Called every 30s from status updates - lightweight, no disk write
     */
    public void updatePeerStatuses(List<Map<String, Object>> agentStatuses) {
        if (agentStatuses == null || agentStatuses.isEmpty()) {
            return;
        }

        for (Map<String, Object> statusData : agentStatuses) {
            String agentId = (String) statusData.get("agentId");
            Boolean online = (Boolean) statusData.get("online");
            Integer fileCount = (Integer) statusData.get("fileCount");

            // Find peer in cache and update status
            Peer peer = cachedPeers.stream()
                    .filter(p -> p.getAgentId().equals(agentId))
                    .findFirst()
                    .orElse(null);

            if (peer != null) {
                peer.setOnline(online);
            }
        }

        // Don't save to disk - these are frequent updates
        // Only peer list structure changes get saved
        log.debug("Updated status for {} peers in memory", agentStatuses.size());
    }

    /**
     * Get only online peers
     */
    public List<Peer> getOnlinePeers() {
        return getAllPeers().stream()
                .filter(Peer::isOnline)
                .toList();
    }

    /**
     * Clear cache (force refresh on next getPeers())
     */
    public void clearCache() {
        cachedPeers.clear();
        log.info("Peer cache cleared");
    }

    /**
     * Load peers from cache file
     */
    private void loadFromCache() {
        File cacheFile = new File(CACHE_FILE);
        if (!cacheFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(cacheFile)) {
            PeerCache cache = gson.fromJson(reader, PeerCache.class);

            if (cache == null) {
                cachedPeers = new ArrayList<>();
                currentPeerListVersion = 0;
            } else {
                cachedPeers = cache.getPeers() != null ? cache.getPeers() : new ArrayList<>();
                currentPeerListVersion = cache.getVersion();
            }

            log.info("Loaded {} peers from cache (version: {})", cachedPeers.size(), currentPeerListVersion);

        } catch (IOException e) {
            log.error("Error loading peer cache: {}", e.getMessage());
            cachedPeers = new ArrayList<>();
            currentPeerListVersion = 0;
        }
    }

    /**
     * Save peers to cache file
     */
    private void saveToCache() {
        try (FileWriter writer = new FileWriter(CACHE_FILE)) {
            PeerCache cache = new PeerCache();
            cache.setPeers(cachedPeers);
            cache.setVersion(currentPeerListVersion);
            gson.toJson(cache, writer);
        } catch (IOException e) {
            log.error("Error saving peer cache: {}", e.getMessage());
        }
    }
}