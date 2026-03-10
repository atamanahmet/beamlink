package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.SseEmitters;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentSseController {

    private final SseEmitters sseEmitters;
    private final AgentService agentService;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseEmitters.add(emitter);

        Agent agent = agentService.getAgent();
        try {
            emitter.send(SseEmitter.event()
                    .name("identity_updated")
                    .data(Map.of(
                            "agentId",     agent.getId() != null ? agent.getId().toString() : "",
                            "agentName",   agent.getAgentName() != null ? agent.getAgentName() : "",
                            "state",       agent.getState() != null ? agent.getState().name() : "",
                            "publicToken", agent.getPublicToken() != null ? agent.getPublicToken() : "",
                            "authToken",   agent.getAuthToken() != null ? agent.getAuthToken() : ""
                    )));
        } catch (Exception e) {
            log.warn("Failed to send initial identity to new SSE client: {}", e.getMessage());
        }

        return emitter;
    }

    @GetMapping("/token")
    public ResponseEntity<String> getPublicToken() {
        String token = agentService.getPublicToken();
        log.info("Token requested. publicToken={}, state={}",
                token != null ? "present" : "null",
                agentService.getState());
        if (token == null) return ResponseEntity.status(503).build();
        return ResponseEntity.ok(token);
    }
}