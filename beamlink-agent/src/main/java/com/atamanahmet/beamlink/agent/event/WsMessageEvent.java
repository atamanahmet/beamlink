package com.atamanahmet.beamlink.agent.event;

import com.atamanahmet.beamlink.agent.dto.WebSocketMessageDTO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WsMessageEvent extends ApplicationEvent {
    private final WebSocketMessageDTO<JsonNode> message;

    public WsMessageEvent(Object source, WebSocketMessageDTO<JsonNode> message) {
        super(source);
        this.message = message;
    }

    public WebSocketMessageDTO<JsonNode> getMessage() {
        return message;
    }
}