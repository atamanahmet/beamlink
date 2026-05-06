package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentStatusDTO;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.agent.event.AgentIdentityChangedEvent;
import com.atamanahmet.beamlink.agent.repository.AgentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock private AgentConfig config;
    @Mock private AgentRepository agentRepository;
    @Mock private LogService logService;
    @Mock private PeerCacheService peerCacheService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentService agentService;

    /* helpers */

    private void setValueFields() {
        ReflectionTestUtils.setField(agentService, "SERVER_PORT", 8080);
        ReflectionTestUtils.setField(agentService, "SERVER_ADDRESS", "192.168.1.1");
    }

    /**
     * Boots a fresh agent,no DB record exists
     */
    private void initFresh() {
        setValueFields();
        when(agentRepository.findById(1L)).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
        agentService.init();
    }

    /**
     * Boots with an existing DB record
     */
    private void initWithExisting(Agent agent) {
        setValueFields();
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        agentService.init();
    }

    private Agent buildAgent(AgentState state) {
        Agent a = new Agent();
        a.setAgentName("TestAgent");
        a.setIpAddress("192.168.1.1");
        a.setPort(8080);
        a.setState(state);
        return a;
    }

    private void stubSave() {
        when(agentRepository.save(any(Agent.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("creates UNREGISTERED agent with default name when no DB record exists")
    void init_createsNewAgent_whenNoRecordExists() {
        initFresh();

        assertThat(agentService.getState()).isEqualTo(AgentState.UNREGISTERED);
        assertThat(agentService.getAgentId()).isNull();
        assertThat(agentService.getAgentName()).isEqualTo("Agent-192.168.1.1:8080");
        verify(agentRepository).save(any(Agent.class));
    }

    @Test
    @DisplayName("loads existing agent from DB without touching it")
    void init_loadsExistingAgent_whenRecordExists() {
        Agent existing = buildAgent(AgentState.APPROVED);
        existing.setAgentId(UUID.randomUUID());
        existing.setAgentName("MyPC");
        initWithExisting(existing);

        assertThat(agentService.getAgentName()).isEqualTo("MyPC");
        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
        /* must not write back just because it loaded */
        verify(agentRepository, never()).save(any());
    }

    @Test
    @DisplayName("ignores APPROVED -> UNREGISTERED — invalid transition")
    void transitionTo_ignores_approvedToUnregistered() {
        initWithExisting(buildAgent(AgentState.APPROVED));

        agentService.transitionTo(AgentState.UNREGISTERED);

        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
        verify(agentRepository, never()).save(any());
    }

    @Test
    @DisplayName("clears tokens when transitioning to PENDING_APPROVAL")
    void transitionTo_clearsTokens_onPendingApproval() {
        Agent agent = buildAgent(AgentState.UNREGISTERED);
        agent.setAuthToken("old-auth");
        agent.setPublicToken("old-public");
        initWithExisting(agent);
        stubSave();

        agentService.transitionTo(AgentState.PENDING_APPROVAL);

        assertThat(agentService.getAuthToken()).isNull();
        assertThat(agentService.getPublicToken()).isNull();
        assertThat(agentService.getState()).isEqualTo(AgentState.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("persists new state on valid transition")
    void transitionTo_persistsState_onValidTransition() {
        initWithExisting(buildAgent(AgentState.UNREGISTERED));
        stubSave();

        agentService.transitionTo(AgentState.PENDING_APPROVAL);

        verify(agentRepository).save(argThat(a -> a.getState() == AgentState.PENDING_APPROVAL));
    }

    @Test
    @DisplayName("allows PENDING_APPROVAL -> APPROVED transition")
    void transitionTo_allowsPendingToApproved() {
        initWithExisting(buildAgent(AgentState.PENDING_APPROVAL));
        stubSave();

        agentService.transitionTo(AgentState.APPROVED);

        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
    }

    @Test
    @DisplayName("wipes identity but keeps name and address, nexus db wiped, agent remembers its PC name")
    void forceReset_wipesIdentity_keepsName() {
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(UUID.randomUUID());
        agent.setAgentName("MyPC");
        agent.setAuthToken("auth");
        agent.setPublicToken("pub");
        initWithExisting(agent);
        stubSave();

        agentService.forceReset();

        assertThat(agentService.getState()).isEqualTo(AgentState.UNREGISTERED);
        assertThat(agentService.getAgentId()).isNull();
        assertThat(agentService.getAuthToken()).isNull();
        assertThat(agentService.getPublicToken()).isNull();
        assertThat(agentService.getAgentName()).isEqualTo("MyPC");
    }

    @Test
    @DisplayName("skips reset when already clean UNREGISTERED with no agentId")
    void forceReset_skips_whenAlreadyClean() {
        initWithExisting(buildAgent(AgentState.UNREGISTERED));

        agentService.forceReset();

        verify(agentRepository, never()).save(any());
    }

    @Test
    @DisplayName("persists reset state to DB")
    void forceReset_persists() {
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(UUID.randomUUID());
        initWithExisting(agent);
        stubSave();

        agentService.forceReset();

        verify(agentRepository).save(argThat(a ->
                a.getState() == AgentState.UNREGISTERED && a.getAgentId() == null));
    }

    @Test
    @DisplayName("stores both tokens and persists")
    void storeTokens_storesBothAndPersists() {
        initFresh();

        agentService.storeTokens("auth-token", "pub-token");

        assertThat(agentService.getAuthToken()).isEqualTo("auth-token");
        assertThat(agentService.getPublicToken()).isEqualTo("pub-token");
        verify(agentRepository, atLeastOnce()).save(argThat(a ->
                "auth-token".equals(a.getAuthToken()) && "pub-token".equals(a.getPublicToken())));
    }

    /*
     * Nexus pushes approval
     *  */

    @Test
    @DisplayName("applies all fields from push request and fires AgentApprovedEvent")
    void applyNexusIdentity_request_appliesFieldsAndFiresApprovedEvent() {
        initFresh();
        UUID assignedId = UUID.randomUUID();

        ApprovalPushRequest req = new ApprovalPushRequest();
        req.setAgentId(assignedId);
        req.setApprovedName("ApprovedAgent");
        req.setAuthToken("auth");
        req.setPublicToken("pub");
        req.setState(AgentState.APPROVED);

        agentService.applyNexusIdentity(req);

        assertThat(agentService.getAgentId()).isEqualTo(assignedId);
        assertThat(agentService.getAgentName()).isEqualTo("ApprovedAgent");
        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    @Test
    @DisplayName("persists all fields from push request to DB")
    void applyNexusIdentity_request_persistsAllFields() {
        initFresh();
        UUID assignedId = UUID.randomUUID();

        ApprovalPushRequest req = new ApprovalPushRequest();
        req.setAgentId(assignedId);
        req.setApprovedName("SavedAgent");
        req.setAuthToken("a");
        req.setPublicToken("p");
        req.setState(AgentState.APPROVED);

        agentService.applyNexusIdentity(req);

        verify(agentRepository, atLeastOnce()).save(argThat(a ->
                assignedId.equals(a.getAgentId())
                        && "SavedAgent".equals(a.getAgentName())
                        && a.getState() == AgentState.APPROVED));
    }

    @Test
    @DisplayName("fires AgentApprovedEvent on first-time approval via identity sync")
    void applyNexusIdentity_response_firesApprovedEvent_whenFirstTimeApproved() {
        initWithExisting(buildAgent(AgentState.PENDING_APPROVAL));
        stubSave();

        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(UUID.randomUUID())
                .agentName("ApprovedAgent")
                .state(AgentState.APPROVED)
                .authToken("auth")
                .publicToken("pub")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    @Test
    @DisplayName("fires AgentIdentityChangedEvent when already approved and identity data changes")
    void applyNexusIdentity_response_firesIdentityChangedEvent_whenAlreadyApprovedAndDataChanges() {
        UUID agentId = UUID.randomUUID();
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(agentId);
        agent.setAgentName("MyPC");
        agent.setAuthToken("old-auth");
        agent.setPublicToken("old-pub");
        initWithExisting(agent);
        stubSave();

        /* same agentId — still approved. but tokens changed */
        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(agentId)
                .agentName("MyPC")
                .state(AgentState.APPROVED)
                .authToken("new-auth")
                .publicToken("new-pub")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher).publishEvent(any(AgentIdentityChangedEvent.class));
    }

    @Test
    @DisplayName("fires no event when already approved and nothing changed")
    void applyNexusIdentity_response_firesNoEvent_whenIdentityUnchanged() {
        UUID agentId = UUID.randomUUID();
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(agentId);
        agent.setAgentName("MyPC");
        agent.setAuthToken("auth");
        agent.setPublicToken("pub");
        initWithExisting(agent);
        stubSave();

        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(agentId)
                .agentName("MyPC")
                .state(AgentState.APPROVED)
                .authToken("auth")
                .publicToken("pub")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("fires AgentApprovedEvent when agentId changes — treated as re-approval")
    void applyNexusIdentity_response_firesApprovedEvent_whenAgentIdChanges() {
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(UUID.randomUUID());
        initWithExisting(agent);
        stubSave();

        /* different UUID — wasAlreadyApproved = false, triggers re-approval */
        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(UUID.randomUUID())
                .agentName("MyPC")
                .state(AgentState.APPROVED)
                .authToken("auth")
                .publicToken("pub")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    @Test
    @DisplayName("persists all identity fields from response")
    void applyNexusIdentity_response_persistsAllFields() {
        initWithExisting(buildAgent(AgentState.PENDING_APPROVAL));
        stubSave();
        UUID agentId = UUID.randomUUID();

        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(agentId)
                .agentName("ApprovedAgent")
                .state(AgentState.APPROVED)
                .authToken("auth")
                .publicToken("pub")
                .build();

        agentService.applyNexusIdentity(response);

        verify(agentRepository, atLeastOnce()).save(argThat(a ->
                agentId.equals(a.getAgentId())
                        && "ApprovedAgent".equals(a.getAgentName())
                        && a.getState() == AgentState.APPROVED));
    }

    @Test
    @DisplayName("updates agent name and persists")
    void updateAgentName_updatesAndPersists() {
        initFresh();

        agentService.updateAgentName("NewName");

        assertThat(agentService.getAgentName()).isEqualTo("NewName");
        verify(agentRepository, atLeastOnce()).save(argThat(a -> "NewName".equals(a.getAgentName())));
    }

    @Test
    @DisplayName("updates agentId and persists")
    void updateAgentId_updatesAndPersists() {
        initFresh();
        UUID newId = UUID.randomUUID();

        agentService.updateAgentId(newId);

        assertThat(agentService.getAgentId()).isEqualTo(newId);
        verify(agentRepository, atLeastOnce()).save(argThat(a -> newId.equals(a.getAgentId())));
    }

    @Test
    @DisplayName("isApproved returns true only when state is APPROVED")
    void isApproved_returnsTrue_whenApproved() {
        initWithExisting(buildAgent(AgentState.APPROVED));
        assertThat(agentService.isApproved()).isTrue();
    }

    @Test
    @DisplayName("isApproved returns false when PENDING_APPROVAL")
    void isApproved_returnsFalse_whenPending() {
        initWithExisting(buildAgent(AgentState.PENDING_APPROVAL));
        assertThat(agentService.isApproved()).isFalse();
    }

    @Test
    @DisplayName("isApproved returns false when UNREGISTERED")
    void isApproved_returnsFalse_whenUnregistered() {
        initFresh();
        assertThat(agentService.isApproved()).isFalse();
    }

    @Test
    @DisplayName("status DTO reflects current agent state")
    void getAgentStatusDTO_reflectsCurrentState() {
        UUID agentId = UUID.randomUUID();
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setAgentId(agentId);
        agent.setAgentName("MyPC");
        initWithExisting(agent);

        when(logService.getUnsyncedLogs()).thenReturn(List.of());
        when(peerCacheService.getCurrentPeerListVersion()).thenReturn(3L);

        AgentStatusDTO dto = agentService.getAgentStatusDTO();

        assertThat(dto.getAgentId()).isEqualTo(agentId);
        assertThat(dto.getAgentName()).isEqualTo("MyPC");
        assertThat(dto.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(dto.getPort()).isEqualTo(8080);
        assertThat(dto.getUnSyncedLogs()).isZero();
        assertThat(dto.getPeerVersion()).isEqualTo(3L);
    }
}