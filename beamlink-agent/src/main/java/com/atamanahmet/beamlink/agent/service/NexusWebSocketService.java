package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.dto.WebSocketMessageDTO;
import com.atamanahmet.beamlink.agent.event.WsConnectionEvent;
import com.atamanahmet.beamlink.agent.event.WsMessageEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class NexusWebSocketService {

    private final Logger log = LoggerFactory.getLogger(NexusWebSocketService.class);

    private final AgentService agentService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private final AtomicBoolean connected = new AtomicBoolean(false);

    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();

    private static final Duration RECONNECT_DELAY = Duration.ofSeconds(10);
    private static final String WS_PATH = "/ws/agents";

    private final ReactorNettyWebSocketClient client = new ReactorNettyWebSocketClient();

    private volatile String nexusBaseUrl;

    /**
     * Connects to Nexus WS. Skips if already connected or token is missing.
     */
    public void connect(String nexusUrl) {
        if (connected.get()) {
            log.debug("Already connected, skipping.");
            return;
        }

        String token = agentService.getAuthToken();
        if (token == null || token.isBlank()) {
            log.warn("Cannot connect WS — no auth token yet.");
            return;
        }

        this.nexusBaseUrl = nexusUrl;

        URI uri = URI.create(nexusUrl
                .replaceFirst("^http://", "ws://")
                .replaceFirst("^https://", "wss://")
                + WS_PATH);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-Token", token);

        log.info("Connecting to Nexus WS at {}...", uri);

        client.execute(uri, headers, this::handleSession)
                .doOnError(err -> {
                    log.warn("WS connection failed: {}", err.getMessage());
                    connected.set(false);
                    scheduleReconnect();
                })
                .subscribe();
    }

    /**
     * Handles active WS session, sets state, listen for messages, reconnects on termination.
     */
    private Mono<Void> handleSession(WebSocketSession session) {
        sessionRef.set(session);
        connected.set(true);
        reconnectScheduled.set(false);
        log.info("Connected to Nexus WS at {}", session.getHandshakeInfo().getUri());

        eventPublisher.publishEvent(new WsConnectionEvent(this, WsConnectionEvent.Type.CONNECTED));

        return session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(this::handleMessage)
                .doOnTerminate(() -> {
                    connected.set(false);
                    sessionRef.set(null);
                    log.warn("WS session terminated. Scheduling reconnect...");
                    eventPublisher.publishEvent(
                            new WsConnectionEvent(this, WsConnectionEvent.Type.DISCONNECTED));
                    scheduleReconnect();
                })
                .then();
    }

    /**
     * Parses incoming WS message and publishes it as an application event.
     */
    private Mono<Void> handleMessage(String raw) {
        try {
            WebSocketMessageDTO<JsonNode> dto = objectMapper.readValue(
                    raw,
                    objectMapper.getTypeFactory().constructParametricType(WebSocketMessageDTO.class, JsonNode.class)
            );
            if (dto.getType() != null) {
                eventPublisher.publishEvent(new WsMessageEvent(this, dto));
            }
        } catch (Exception e) {
            log.error("Failed to parse WS message: {}", e.getMessage());
        }
        return Mono.empty();
    }

    /**
     * Schedules a single reconnect attempt after delay. Duplicate calls are ignored.
     */
    private void scheduleReconnect() {
        if (!reconnectScheduled.compareAndSet(false, true)) {
            log.debug("Reconnect already scheduled, skipping duplicate.");
            return;
        }

        Mono.delay(RECONNECT_DELAY)
                .doOnNext(l -> log.info("Attempting WS reconnect..."))
                .doOnError(err -> {
                    log.warn("Reconnect error: {}", err.getMessage());
                    reconnectScheduled.set(false);
                })
                .onErrorComplete()
                .subscribe(l -> {
                    reconnectScheduled.set(false); // clear BEFORE connect() so it can proceed
                    connect(nexusBaseUrl);
                });
    }

    /**
     * Sends a message over the active WS session. Logs warning if session is not open.
     */
    public void send(Object dto) {
        WebSocketSession session = sessionRef.get();
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(dto);
                session.send(Mono.just(session.textMessage(json)))
                        .doOnError(e -> {
                            log.error("WS send failed: {}", e.getMessage());
                            eventPublisher.publishEvent(
                                    new WsConnectionEvent(this, WsConnectionEvent.Type.DISCONNECTED));
                        })
                        .subscribe();
            } catch (Exception e) {
                log.error("Failed to serialize WS message: {}", e.getMessage());
            }
        } else {
            log.warn("Cannot send — WS session not open.");
        }
    }

    public boolean isConnected() {
        return connected.get();
    }
}