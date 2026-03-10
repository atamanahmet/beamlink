package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationRequest;
import com.atamanahmet.beamlink.nexus.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.nexus.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.nexus.exception.AgentNotFoundException;
import com.atamanahmet.beamlink.nexus.exception.NameAlreadyInUseException;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private PeerListService peerListService;

    @Mock
    private AgentTokenService agentTokenService;

    @Mock
    private AgentPushService agentPushService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentService agentService;

        @Test
    @DisplayName("context loads")
    void contextLoads() {
    }

    @Test
    @DisplayName("returns existing agent when already registered at ip and port")
    void register_returnsExistingAgent_whenAlreadyRegistered() {
        Agent existing = buildAgent(AgentState.PENDING_APPROVAL);
        when(agentRepository.findByIpAddressAndPort("192.168.1.10", 9090))
                .thenReturn(Optional.of(existing));

        AgentRegistrationRequest request = new AgentRegistrationRequest();
        request.setIpAddress("192.168.1.10");
        request.setPort(9090);

        AgentRegistrationResponse response = agentService.register(request);

        assertThat(response.agentId()).isEqualTo(existing.getId());
        verify(agentRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("throws when approving agent that is not pending")
    void approveAgent_throws_whenAgentNotPending() {
        Agent agent = buildAgent(AgentState.APPROVED);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentService.approveAgent(agent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    @DisplayName("throws when rejecting agent that is not pending")
    void rejectAgent_throws_whenAgentNotPending() {
        Agent agent = buildAgent(AgentState.APPROVED);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentService.rejectAgent(agent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not pending approval");
    }

    @Test
    @DisplayName("throws when requested rename is already taken")
    void requestRename_throws_whenNameAlreadyInUse() {
        Agent agent = buildAgent(AgentState.APPROVED);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(agentRepository.existsByName("TakenName")).thenReturn(true);

        assertThatThrownBy(() -> agentService.requestRename(agent.getId(), "TakenName"))
                .isInstanceOf(NameAlreadyInUseException.class);
    }

    @Test
    @DisplayName("throws when approving rename with no pending request")
    void approveRename_throws_whenNoPendingRename() {
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setRequestedName(null);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> agentService.approveRename(agent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No pending rename request");
    }

    @Test
    @DisplayName("fires AgentApprovedEvent when agent is approved")
    void approveAgent_firesApprovedEvent_whenAgentApproved() {
        Agent agent = buildAgent(AgentState.PENDING_APPROVAL);
        when(agentRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(agentRepository.saveAndFlush(any())).thenReturn(agent);
        when(agentTokenService.generateAuthToken(any())).thenReturn("auth-token");
        when(agentTokenService.generatePublicToken(any(), any())).thenReturn("public-token");

        agentService.approveAgent(agent.getId());

        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    private Agent buildAgent(AgentState state) {
        return Agent.builder()
                .id(UUID.randomUUID())
                .name("TestAgent")
                .ipAddress("192.168.1.10")
                .port(9090)
                .state(state)
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
    }
}