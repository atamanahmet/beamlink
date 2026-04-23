package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentRenameResponse;
import com.atamanahmet.beamlink.nexus.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.nexus.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.websocket.AgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPushService {

    private final AgentRepository agentRepository;
    private final AgentWebSocketHandler webSocketHandler;
    private final AgentTokenService agentTokenService;
    private final WebClient.Builder webClientBuilder;

    @Transactional
    @Scheduled(fixedDelay = 30_000)
    public void pushPendingApprovals() {
        List<Agent> unpushed = agentRepository
                .findByStateAndApprovalPushedFalse(AgentState.APPROVED);

        if (unpushed.isEmpty()) return;

        log.info("Retrying approval push to {} agent(s).", unpushed.size());

        for (Agent agent : unpushed) {
            if (agent.getPublicId() == null) {
                log.warn("Skipping approval push for agent {}, publicId is null.", agent.getId());
                continue;
            }
            String authToken = agentTokenService.generateAuthToken(agent.getId());
            String publicToken = agentTokenService.generatePublicToken(agent.getId(), agent.getPublicId());
            pushApproval(agent, authToken, publicToken);
        }
    }

    public void pushApproval(Agent agent, String authToken, String publicToken) {
        ApprovalPushRequest payload = ApprovalPushRequest.builder()
                .agentId(agent.getId())
                .approvedName(agent.getName())
                .authToken(authToken)
                .publicToken(publicToken)
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

    private void pushViaWebSocket(Agent agent, ApprovalPushRequest payload) {
        try {
            webSocketHandler.sendMessage(agent.getId(), Map.of(
                    "type", "approval_push",
                    "payload", payload));
            markPushed(agent);
            log.info("Approval pushed via WS to agent {}", agent.getId());
        } catch (Exception e) {
            log.warn("WS push failed for agent {}, will retry in next schedule: {}",
                    agent.getId(), e.getMessage());
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
            log.warn("HTTP push failed for agent {} at {}, will retry in next schedule: {}",
                    agent.getId(), url, e.getMessage());
        }
    }

    private void pushRenameViaWebSocket(Agent agent) {
        try {
            webSocketHandler.sendMessage(agent.getId(), Map.of(
                    "type", "rename_request",
                    "payload", AgentRenameResponse.builder()
                            .agentName(agent.getName())
                            .build()));
            log.info("Rename pushed via WS to agent {}: {}", agent.getId(), agent.getName());
        } catch (Exception e) {
            log.warn("WS rename push failed for agent {}, falling back to HTTP: {}",
                    agent.getId(), e.getMessage());
            pushRenameViaHttp(agent);
        }
    }

    private void pushRenameViaHttp(Agent agent) {
        String url = buildUrl(agent, "/api/agents/rename");
        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(AgentRenameResponse.builder()
                            .agentName(agent.getName())
                            .build())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            log.info("Rename pushed via HTTP to agent {} at {}", agent.getId(), url);
        } catch (Exception e) {
            log.warn("HTTP rename push failed for agent {} at {}: {}",
                    agent.getId(), url, e.getMessage());
        }
    }

    public void markPushed(Agent agent) {
        agentRepository.markApprovalPushed(agent.getId());
    }

    private String buildUrl(Agent agent, String path) {
        return "http://" + agent.getIpAddress() + ":" + agent.getPort() + path;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAgentApproved(AgentApprovedEvent event) {
        pushApproval(event.agent(), event.authToken(), event.publicToken());
    }
}