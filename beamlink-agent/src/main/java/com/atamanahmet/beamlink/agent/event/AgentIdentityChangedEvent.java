package com.atamanahmet.beamlink.agent.event;

import org.springframework.context.ApplicationEvent;

public class AgentIdentityChangedEvent extends ApplicationEvent {
    public AgentIdentityChangedEvent(Object source) {
        super(source);
    }
}