package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.dto.AgentDTO;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.dto.StatusUpdatePayload;
import com.atamanahmet.beamlink.nexus.dto.WebSocketMessageDTO;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.service.AgentService;
import com.atamanahmet.beamlink.nexus.service.AgentSessionService;
import com.atamanahmet.beamlink.nexus.service.PeerListService;
import com.atamanahmet.beamlink.nexus.service.TransferLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class AgentWebSocketHandler implements WebSocketHandler {

    private final Logger log = LoggerFactory.getLogger(AgentWebSocketHandler.class);

    private final AgentSessionService agentSessionService;
    private final PeerListService peerListService;
    private final ObjectMapper objectMapper;
    private final NexusConfig nexusConfig;
    private final AgentTokenService agentTokenService;
    private final TransferLogService transferLogService;

    private final Map<UUID, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID agentId = getAgentId(session);
        if (agentId != null) {
            sessions.put(agentId, session);
            log.info("Agent {} connected via WS", agentId);
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        WebSocketMessageDTO<JsonNode> envelope = objectMapper.readValue(
                message.getPayload().toString(),
                objectMapper.getTypeFactory().constructParametricType(WebSocketMessageDTO.class, JsonNode.class)
        );

        switch (envelope.getType()) {
            case "status_update" -> handleStatusUpdate(session,
                    objectMapper.treeToValue(envelope.getPayload(), StatusUpdatePayload.class));
            case "peer_update"   -> handlePeerUpdate(session,
                    objectMapper.treeToValue(envelope.getPayload(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, AgentDTO.class)));
            case "log_sync" -> handleLogSync(session,
                    objectMapper.treeToValue(envelope.getPayload(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, LogSyncRequest.class)));
            default -> log.warn("Unknown WS message type: {}", envelope.getType());
        }
    }

    private void handleLogSync(WebSocketSession session, List<LogSyncRequest> logs) {
        try {
            UUID agentId = getAgentId(session);
            if (agentId == null) {
                log.warn("log_sync received but session has no agentId attribute");
                return;
            }

            List<UUID> syncedIds = transferLogService.sync(agentId, logs);
            log.debug("Log sync from agent {}: {}/{} logs accepted", agentId, syncedIds.size(), logs.size());

        } catch (Exception e) {
            log.error("Error handling log_sync for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void handlePeerUpdate(WebSocketSession session, List<?> payloadList) {
        try {
            UUID agentId = getAgentId(session);
            if (agentId == null) {
                log.warn("peer_update received but session has no agentId attribute");
                return;
            }

            // Here you can implement logic to handle peer list updates
            // For example, you might log or refresh peer info in Nexus
            log.debug("Received peer_update from agent {} with {} peers", agentId, payloadList.size());

            // Optional: validate list contents
            for (Object item : payloadList) {
                if (!(item instanceof Map)) {
                    log.warn("peer_update item is not a Map: {}", item.getClass().getSimpleName());
                }
            }

        } catch (Exception e) {
            log.error("Error handling peer_update for session {}: {}", session.getId(), e.getMessage());
        }
    }

    private void handleStatusUpdate(WebSocketSession session, StatusUpdatePayload payload) {
        try {
            UUID agentId = getAgentId(session);
            if (agentId == null) {
                log.warn("status_update received but session has no agentId attribute");
                return;
            }

            Agent agent = agentSessionService.findById(agentId);
            agent.setLastSeenAt(Instant.now());
            agentSessionService.save(agent);

            if (payload != null && peerListService.isPeerListOutdated(payload.getPeerVersion())) {
                pushPeerUpdate(session, agentId);
            }

            log.debug("Status updated for agent {}", agentId);

        } catch (Exception e) {
            log.error("Error handling status_update for session {}: {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * Excludes agents own ip
     * Adds nexus as a peer
     * */
    private void pushPeerUpdate(WebSocketSession session, UUID agentId) {
        try {
            List<AgentDTO> peers = new ArrayList<>();
            peers.add(buildNexusPeer());
            agentSessionService.findApproved().stream()
                    .filter(a -> !a.getId().equals(agentId))
                    .map(agentSessionService::toDTO)
                    .forEach(peers::add);

            WebSocketMessageDTO<List<AgentDTO>> message = WebSocketMessageDTO.<List<AgentDTO>>builder()
                    .type("peer_update")
                    .version(peerListService.getCurrentVersion())
                    .payload(peers)
                    .build();

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
            log.debug("Pushed peer_update to agent {} ({} peers)", agentId, peers.size());

        } catch (Exception e) {
            log.error("Failed to push peer_update to agent {}: {}", agentId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        UUID agentId = getAgentId(session);
        if (agentId != null) {
            sessions.remove(agentId);
            log.info("Agent {} WS session closed: {}", agentId, closeStatus);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    public void sendMessage(UUID agentId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(agentId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
                log.debug("Sent message to agent {}", agentId);
            } catch (Exception e) {
                log.error("Failed to send message to agent {}: {}", agentId, e.getMessage());
            }
        } else {
            log.debug("No open WS session for agent {} — message not sent", agentId);
        }
    }

    public boolean isConnected(UUID agentId) {
        WebSocketSession session = sessions.get(agentId);
        return session != null && session.isOpen();
    }

    // Helper, reads agentId from handshake interceptor
    private UUID getAgentId(WebSocketSession session) {
        Object attr = session.getAttributes().get(AgentHandshakeInterceptor.AGENT_ID_ATTR);
        return (attr instanceof UUID) ? (UUID) attr : null;
    }

    //TODO: move to peerlistservice
    private AgentDTO buildNexusPeer() {
        String token = agentTokenService.generatePublicToken(UUID.fromString("00000000-0000-0000-0000-000000000000"),"Nexus");

        return AgentDTO.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000000"))
                .agentName("Nexus")
                .ipAddress(nexusConfig.getIpAddress())
                .port(nexusConfig.getNexusPort())
                .online(true)
                .publicToken(token)
                .build();
    }
}