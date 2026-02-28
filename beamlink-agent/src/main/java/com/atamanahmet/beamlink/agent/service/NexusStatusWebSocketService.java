package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.domain.Peer;
import com.atamanahmet.beamlink.agent.dto.AgentStatusDTO;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.dto.WebSocketMessageDTO;
import com.atamanahmet.beamlink.agent.event.WsConnectionEvent;
import com.atamanahmet.beamlink.agent.event.WsMessageEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NexusStatusWebSocketService {

    private final Logger log = LoggerFactory.getLogger(NexusStatusWebSocketService.class);
    private final ObjectMapper objectMapper;

    private final AgentService agentService;
    private final LogService logService;
    private final PeerCacheService peerCacheService;
    private final NexusConnectionStateService connectionState;
    private final NexusWebSocketService nexusWebSocketService;

    /**
     * Listeners for ws (to avoid circular dependency)
     * */
    @EventListener
    public void onWsConnectionEvent(WsConnectionEvent event) {
        if (event.getType() == WsConnectionEvent.Type.CONNECTED) {
            connectionState.reportOnline();
        } else {
            connectionState.reportOffline();
        }
    }

    @EventListener
    public void onWsMessage(WsMessageEvent event) {
        handleMessage(event.getMessage());
    }

    /**
     * Sync with nexus
     * */
    @Scheduled(fixedRate = 30_000)
    public void sendStatus() {
        if (!agentService.isApproved() || !nexusWebSocketService.isConnected()) return;

        AgentStatusDTO statusDTO = agentService.getAgentStatusDTO();
        WebSocketMessageDTO<AgentStatusDTO> msg = WebSocketMessageDTO.<AgentStatusDTO>builder()
                .type("status_update")
                .payload(statusDTO)
                .build();

        nexusWebSocketService.send(msg);
        log.debug("Status sent over WS: {}", statusDTO);
    }

    @Scheduled(fixedRate = 60_000)
    public void syncLogs() {
        if (!agentService.isApproved() || !nexusWebSocketService.isConnected()) return;

        List<TransferLog> unsyncedLogs = logService.getUnsyncedLogs();
        if (unsyncedLogs.isEmpty()) return;

        WebSocketMessageDTO<List<TransferLog>> msg = WebSocketMessageDTO.<List<TransferLog>>builder()
                .type("log_sync")
                .payload(unsyncedLogs)
                .build();

        nexusWebSocketService.send(msg);
        log.debug("Sent {} unsynced logs over WS", unsyncedLogs.size());
    }

    /**
     * ws message
     * */
    private void handleMessage(WebSocketMessageDTO<JsonNode> message) {
        if (message == null || message.getType() == null) return;

        switch (message.getType()) {
            case "approval_push"  -> handleApprovalPush(message);
            case "peer_update"    -> handlePeerUpdate(message);
            case "rename_request" -> handleRename(message);
            default -> log.warn("Unknown WS message type: {}", message.getType());
        }
    }

    private void handleApprovalPush(WebSocketMessageDTO<JsonNode> message) {
        try {
            ApprovalPushRequest request = objectMapper.treeToValue(message.getPayload(), ApprovalPushRequest.class);
            agentService.applyNexusIdentity(request);
            log.info("Agent approved via WS. Name={}, Id={}",
                    agentService.getAgentName(), agentService.getAgentId());
        } catch (Exception e) {
            log.error("Failed to apply approval push: {}", e.getMessage(), e);
        }
    }

    private void handlePeerUpdate(WebSocketMessageDTO<JsonNode> message) {
        try {
            List<Peer> peers = objectMapper.convertValue(
                    message.getPayload(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Peer.class)
            );
            long version = message.getVersion() != null ? message.getVersion() : 0L;
            peerCacheService.updatePeers(peers, version);
            log.debug("Peer update applied via WS, {} peers (version: {})", peers.size(), version);
        } catch (Exception e) {
            log.error("Failed to handle peer update: {}", e.getMessage(), e);
        }
    }

    private void handleRename(WebSocketMessageDTO<JsonNode> message) {
        try {
            String newName = message.getPayload().get("agentName").asText();
            if (newName != null && !newName.isBlank()) {
                agentService.updateAgentName(newName);
                log.info("Agent renamed via WS: {}", newName);
            }
        } catch (Exception e) {
            log.error("Failed to handle rename request: {}", e.getMessage(), e);
        }
    }
}