package com.atamanahmet.beamlink.agent.event;

import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.SseEmitters;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AgentIdentityEventListener {

    private final SseEmitters sseEmitters;
    private final AgentService agentService;

    @EventListener
    public void onApproved(AgentApprovedEvent event) {
        broadcast();
    }

    @EventListener
    public void onIdentityChanged(AgentIdentityChangedEvent event) {
        broadcast();
    }

    private void broadcast() {
        Agent agent = agentService.getAgent();
        sseEmitters.broadcast("identity_updated", Map.of(
                "agentId",     agent.getId() != null ? agent.getId().toString() : "",
                "agentName",   agent.getAgentName() != null ? agent.getAgentName() : "",
                "state",       agent.getState() != null ? agent.getState().name() : "",
                "publicToken", agent.getPublicToken() != null ? agent.getPublicToken() : "",
                "authToken",   agent.getAuthToken() != null ? agent.getAuthToken() : ""
        ));
    }
}