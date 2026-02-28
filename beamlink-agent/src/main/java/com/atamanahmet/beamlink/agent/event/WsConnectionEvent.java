package com.atamanahmet.beamlink.agent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class WsConnectionEvent extends ApplicationEvent {

    public enum Type { CONNECTED, DISCONNECTED }

    private final Type type;

    public WsConnectionEvent(Object source, Type type) {
        super(source);
        this.type = type;
    }
}