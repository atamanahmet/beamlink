package com.atamanahmet.beamlink.nexus.startup;

import com.atamanahmet.beamlink.nexus.service.PeerListService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PeerListInitializer {

    private final PeerListService peerListService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        peerListService.initialize();
    }
}