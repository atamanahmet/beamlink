package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentRenameResponse;
import com.atamanahmet.beamlink.nexus.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.websocket.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentPushService {

    private final Logger log = LoggerFactory.getLogger(AgentPushService.class);

    private final AgentRepository agentRepository;
    private final AgentWebSocketHandler webSocketHandler;
    private final WebClient.Builder webClientBuilder;

    @Scheduled(fixedDelay = 30_000)
    public void pushPendingApprovals() {
        List<Agent> unpushed = agentRepository
                .findByStateAndApprovalPushedFalse(AgentState.APPROVED);

        if (unpushed.isEmpty()) return;

        log.info("Pushing approval to {} agent(s).", unpushed.size());

        for (Agent agent : unpushed) {
            pushApproval(agent);
        }
    }

    public void pushApproval(Agent agent) {
        ApprovalPushRequest payload = ApprovalPushRequest.builder()
                .agentId(agent.getId())
                .approvedName(agent.getName())
                .authToken(agent.getAuthToken())
                .publicToken(agent.getPublicToken())
                .state(AgentState.APPROVED)
                .build();

        if (webSocketHandler.isConnected(agent.getId())) {
            pushViaWebSocket(agent, payload);
        } else {
            pushViaHttp(agent, payload);
        }
    }

    public void pushRename(Agent agent) {
        if (webSocketHandler.isConnected(agent.getId())) {
            pushRenameViaWebSocket(agent);
        } else {
            pushRenameViaHttp(agent);
        }
    }

    private void pushRenameViaWebSocket(Agent agent) {
        try {
            AgentRenameResponse payload = AgentRenameResponse.builder()
                    .agentName(agent.getName())
                    .build();

            webSocketHandler.sendMessage(agent.getId(), Map.of(
                    "type", "rename_request",
                    "payload", payload
            ));
            log.info("Rename pushed via WS to agent {}: {}", agent.getId(), agent.getName());
        } catch (Exception e) {
            log.warn("WS rename push failed for agent {} — falling back to HTTP: {}",
                    agent.getId(), e.getMessage());
            pushRenameViaHttp(agent);
        }
    }

    private void pushRenameViaHttp(Agent agent) {
        String url = buildUrl(agent, "/api/agents/rename");
        try {
            AgentRenameResponse payload = AgentRenameResponse.builder()
                    .agentName(agent.getName())
                    .build();

            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Rename pushed via HTTP to agent {} at {}", agent.getId(), url);
        } catch (Exception e) {
            log.warn("HTTP rename push failed for agent {} at {}: {}",
                    agent.getId(), url, e.getMessage());
        }
    }

    private void pushViaWebSocket(Agent agent, ApprovalPushRequest payload) {
        try {
            webSocketHandler.sendMessage(agent.getId(), Map.of(
                    "type", "approval_push",
                    "payload", payload
            ));
            markPushed(agent);
            log.info("Approval pushed via WS to agent {}", agent.getId());
        } catch (Exception e) {
            log.warn("WS push failed for agent {} — falling back to HTTP: {}",
                    agent.getId(), e.getMessage());
            pushViaHttp(agent, payload);
        }
    }

    private void pushViaHttp(Agent agent, ApprovalPushRequest payload) {
        String url = buildUrl(agent, "/api/approval");
        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            markPushed(agent);
            log.info("Approval pushed via HTTP to agent {} at {}", agent.getId(), url);
        } catch (Exception e) {
            log.warn("HTTP push failed for agent {} at {} — will retry: {}",
                    agent.getId(), url, e.getMessage());
        }
    }

    private void markPushed(Agent agent) {
        agent.setApprovalPushed(true);
        agentRepository.save(agent);
    }

    private String buildUrl(Agent agent, String path) {
        return "http://" + agent.getIpAddress() + ":" + agent.getPort() + path;
    }
}