package com.atamanahmet.beamlink.agent.event;

import org.springframework.context.ApplicationEvent;

public class AgentApprovedEvent extends ApplicationEvent {
    public AgentApprovedEvent(Object source) {
        super(source);
    }
}