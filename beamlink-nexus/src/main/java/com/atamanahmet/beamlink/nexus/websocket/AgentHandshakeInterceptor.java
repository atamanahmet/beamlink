package com.atamanahmet.beamlink.nexus.websocket;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentHandshakeInterceptor implements HandshakeInterceptor {

    private final AgentTokenService agentTokenService;
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
            log.warn("WS handshake rejected, missing X-Auth-Token");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        UUID agentId;
        try {
            agentId = agentTokenService.extractAgentId(token);
        } catch (InvalidTokenException e) {
            log.warn("WS handshake rejected, invalid token: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            log.warn("WS handshake rejected, unknown agent: {}", agentId);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (agent.getState() != AgentState.APPROVED) {
            log.warn("WS handshake rejected, agent {} not approved", agentId);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }

        attributes.put(AGENT_ID_ATTR, agentId);
        log.info("WS handshake accepted for agent {}", agentId);
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