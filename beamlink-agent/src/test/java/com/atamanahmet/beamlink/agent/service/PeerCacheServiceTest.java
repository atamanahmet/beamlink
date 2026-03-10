package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.dto.PeerStatusUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class PeerCacheServiceTest {


    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PeerCacheService peerCacheService;

    @Test
    @DisplayName("updates peer list and version when peers received")
    void updatePeers_storesPeersAndVersion() {

        UUID id = UUID.randomUUID();
        List<Peer> peers = List.of(buildPeer(id, true));

        peerCacheService.updatePeers(peers, 5L);

        assertThat(peerCacheService.getCurrentPeerListVersion()).isEqualTo(5L);
    }

    @Test
    @DisplayName("updates online status of matching peer")
    void updatePeerStatuses_updatesOnlineStatus_whenPeerMatches() {

        UUID id = UUID.randomUUID();
        peerCacheService.updatePeers(List.of(buildPeer(id, true)), 1L);

        PeerStatusUpdate status = new PeerStatusUpdate();
        status.setAgentId(id.toString());
        status.setOnline(false);

        peerCacheService.updatePeerStatuses(List.of(status));

        boolean isOnline = peerCacheService.getAllPeers(null, null)
                .stream()
                .filter(p -> p.getAgentId().equals(id))
                .findFirst()
                .map(Peer::isOnline)
                .orElseThrow();

        assertThat(isOnline).isFalse();
    }

    @Test
    @DisplayName("clears all peers from cache")
    void clearCache_removesAllPeers() {

        peerCacheService.updatePeers(List.of(buildPeer(UUID.randomUUID(), true)), 1L);

        peerCacheService.clearCache();

        assertThat(peerCacheService.getAllPeers(null, null)).isEmpty();
    }

    private Peer buildPeer(UUID id, boolean online) {
        Peer peer = new Peer();
        peer.setAgentId(id);
        peer.setOnline(online);
        peer.setAgentName("Peer-" + id);
        return peer;
    }
}