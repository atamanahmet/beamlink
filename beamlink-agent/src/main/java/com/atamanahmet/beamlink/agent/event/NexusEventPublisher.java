package com.atamanahmet.beamlink.agent.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NexusEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publishOffline() {
        publisher.publishEvent(new NexusOfflineEvent());
    }

    public void publishOnline() {
        publisher.publishEvent(new NexusOnlineEvent());
    }

    public void publishLostAgent(String reason) {
        publisher.publishEvent(new NexusLostAgentEvent(reason));
    }
}