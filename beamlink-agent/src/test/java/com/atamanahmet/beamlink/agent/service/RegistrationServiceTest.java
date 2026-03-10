package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import com.atamanahmet.beamlink.agent.event.NexusLostAgentEvent;
import com.atamanahmet.beamlink.agent.event.NexusOnlineEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private AgentConfig config;

    @Mock
    private AgentService agentService;

    @Mock
    private WebClient nexusWebClient;

    @Mock
    private NexusEventPublisher nexusEventPublisher;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    @DisplayName("does not register when nexus comes online and agent is not UNREGISTERED")
    void onNexusOnline_skipsRegistration_whenAgentAlreadyRegistered() {

        when(agentService.getState()).thenReturn(AgentState.APPROVED);

        registrationService.onNexusOnline(new NexusOnlineEvent());

        verify(nexusWebClient, never()).post();
    }

    @Test
    @DisplayName("triggers registration when nexus comes online and agent is UNREGISTERED")
    void onNexusOnline_triggersRegistration_whenAgentIsUnregistered() {
        when(agentService.getState()).thenReturn(AgentState.UNREGISTERED);
        when(nexusWebClient.post()).thenReturn(mock(WebClient.RequestBodyUriSpec.class));

        registrationService.onNexusOnline(new NexusOnlineEvent());

        verify(nexusWebClient).post();
    }

    @Test
    @DisplayName("forces resets and triggers registration when nexus forget agent")
    void onNexusLostAgent_forcesResetAndRegisters() {
        when(nexusWebClient.post()).thenReturn(mock(WebClient.RequestBodyUriSpec.class));

        registrationService.onNexusLostAgent(new NexusLostAgentEvent("test-reason"));

        verify(agentService).forceReset();
        verify(nexusWebClient).post();
    }

    @Test
    @DisplayName("skips registration when there is an active one already")
    void registerWithNexus_skipsRegistration_whenAlreadyInProgress() {
        AtomicBoolean inProgress = new AtomicBoolean(true);
        ReflectionTestUtils.setField(registrationService, "registrationInProgress", inProgress);

        registrationService.registerWithNexus();

        verify(nexusWebClient, never()).post();
    }
}