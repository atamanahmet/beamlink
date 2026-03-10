package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class NexusConnectionStateServiceTest {

    @Mock
    private NexusEventPublisher nexusEventPublisher;

    @InjectMocks
    private NexusConnectionStateService service;

    @Test
    @DisplayName("publishes offline event and marks nexus as offline on first report")
    void reportOffline_publishesOfflineEvent_whenNexusWasOnline() {
        service.reportOffline();

        assertThat(service.isNexusConsideredOffline()).isTrue();
        verify(nexusEventPublisher).publishOffline();
    }

    @Test
    @DisplayName("does not publish offline event again if nexus is already offline")
    void reportOffline_doesNotPublishAgain_whenAlreadyOffline() {
        service.reportOffline();
        service.reportOffline();

        verify(nexusEventPublisher).publishOffline();
    }

    @Test
    @DisplayName("publishes online event and marks nexus as online when it comes back")
    void reportOnline_publishesOnlineEvent_whenNexusWasOffline() {
        service.reportOffline();
        service.reportOnline();

        assertThat(service.isNexusConsideredOffline()).isFalse();
        verify(nexusEventPublisher).publishOnline();
    }

    @Test
    @DisplayName("does not publish online event if nexus was never offline")
    void reportOnline_doesNotPublish_whenNexusWasNeverOffline() {
        service.reportOnline();

        assertThat(service.isNexusConsideredOffline()).isFalse();
        verifyNoMoreInteractions(nexusEventPublisher);
    }
}