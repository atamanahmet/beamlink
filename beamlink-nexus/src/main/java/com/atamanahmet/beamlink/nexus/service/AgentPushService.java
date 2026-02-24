package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
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
    private final WebClient.Builder webClientBuilder;

    /**
     * Runs every 30 seconds
     * Finds all APPROVED agents that haven't been successfully notified yet
     * And attempts to push their credentials
     */
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

    /**
     * Pushes approval (authToken, publicToken) to the agent.
     * If the agent is reachable, marks approvalPushed = true.
     * If not, leaves the flag false so the scheduler retries.
     */
    public void pushApproval(Agent agent) {

        String url = buildUrl(agent, "/api/approval");

        ApprovalPushRequest payload = ApprovalPushRequest.builder()
                .agentId(agent.getId())
                .approvedName(agent.getName())
                .authToken(agent.getAuthToken())
                .publicToken(agent.getPublicToken())
                .state(AgentState.APPROVED)
                .build();

        try {
            webClientBuilder.build()
                    .post()
                    .uri(url)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            agent.setApprovalPushed(true);
            agentRepository.save(agent);

            log.info("Approval pushed successfully to agent {} at {}.", agent.getId(), url);

        } catch (Exception e) {
            log.warn("Failed to push approval to agent {} at {} — will retry. Reason: {}",
                    agent.getId(), url, e.getMessage());
        }
    }

    private String buildUrl(Agent agent, String path) {
        return "http://" + agent.getIpAddress() + ":" + agent.getPort() + path;
    }
}