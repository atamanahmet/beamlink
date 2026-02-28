package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AgentHandshakeInterceptor implements HandshakeInterceptor {

    private final Logger log = LoggerFactory.getLogger(AgentHandshakeInterceptor.class);

    private final AgentRepository agentRepository;

    public static final String AGENT_ID_ATTR = "agentId";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = request.getHeaders().getFirst("X-Auth-Token");

        if (token == null || token.isBlank()) {
            log.warn("WS handshake rejected — missing X-Auth-Token");
            return false;
        }

        Optional<Agent> agentOpt = agentRepository.findByAuthToken(token);

        if (agentOpt.isEmpty()) {
            log.warn("WS handshake rejected - unknown token");
            return false;
        }

        Agent agent = agentOpt.get();

        if (agent.getState() != AgentState.APPROVED) {
            log.warn("WS handshake rejected  -agent {} not approved", agent.getId());
            return false;
        }

        attributes.put(AGENT_ID_ATTR, agent.getId());

        log.info("WS handshake accepted for agent {}", agent.getId());
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
    }
}