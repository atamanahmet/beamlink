package com.atamanahmet.beamlink.agent.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.AgentState;

import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.ApprovalPushRequest;
import com.atamanahmet.beamlink.agent.event.AgentApprovedEvent;
import com.atamanahmet.beamlink.agent.event.AgentIdentityChangedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Comparator;
import java.util.UUID;


@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentConfig config;

    @Mock
    private LogService logService;

    @Mock
    private PeerCacheService peerCacheService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AgentService agentService;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("agent-test");

        String infoFile = tempDir.resolve("agent_info.json").toString();
        String infoFileTmp = tempDir.resolve("agent_info.json.tmp").toString();

        ReflectionTestUtils.setField(agentService, "infoFilePath", infoFile);
        ReflectionTestUtils.setField(agentService, "infoFileTmpPath", infoFileTmp);
        ReflectionTestUtils.setField(agentService, "SERVER_PORT", 8080);
        ReflectionTestUtils.setField(agentService, "SERVER_ADDRESS", "192.168.1.1");

        agentService.init();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    @DisplayName("creates new agent with UNREGISTERED state when no info file exists")
    void init_createsNewAgent_whenNoFileExists() {
        AgentState state = agentService.getState();

        assertThat(state).isEqualTo(AgentState.UNREGISTERED);
    }

    @Test
    @DisplayName("loads existing agent from file when info file exists")
    void init_loadsAgent_whenFileExists() throws IOException {
        String existingJson = """
            {
              "id": "11111111-1111-1111-1111-111111111111",
              "agentName": "TestAgent",
              "ipAddress": "192.168.1.1",
              "port": 8080,
              "state": "APPROVED"
            }
            """;

        String infoFilePath = tempDir.resolve("agent_info.json").toString();
        Files.writeString(Path.of(infoFilePath), existingJson);

        agentService.init();

        assertThat(agentService.getAgentName()).isEqualTo("TestAgent");
        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
    }

    @Test
    @DisplayName("ignores transition from APPROVED to UNREGISTERED")
    void transitionTo_ignoresInvalidTransition_whenApprovedToUnregistered() {
        ReflectionTestUtils.setField(agentService, "agent",
                buildAgent(AgentState.APPROVED));

        agentService.transitionTo(AgentState.UNREGISTERED);

        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
    }

    @Test
    @DisplayName("clears tokens when transitioning to PENDING_APPROVAL")
    void transitionTo_clearsTokens_whenTransitioningToPendingApproval() {
        Agent agent = buildAgent(AgentState.UNREGISTERED);
        agent.setAuthToken("some-auth-token");
        agent.setPublicToken("some-public-token");
        ReflectionTestUtils.setField(agentService, "agent", agent);

        agentService.transitionTo(AgentState.PENDING_APPROVAL);

        assertThat(agentService.getAuthToken()).isNull();
        assertThat(agentService.getPublicToken()).isNull();
    }

    @Test
    @DisplayName("resets agent to UNREGISTERED and clears identity when forced")
    void forceReset_clearsIdentity_whenAgentHasData() {
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setId(UUID.randomUUID());
        agent.setAuthToken("some-token");
        agent.setPublicToken("some-public-token");
        ReflectionTestUtils.setField(agentService, "agent", agent);

        agentService.forceReset();

        assertThat(agentService.getState()).isEqualTo(AgentState.UNREGISTERED);
        assertThat(agentService.getAgentId()).isNull();
        assertThat(agentService.getAuthToken()).isNull();
        assertThat(agentService.getPublicToken()).isNull();
    }

    @Test
    @DisplayName("applies nexus identity and fires AgentApprovedEvent when approval pushed")
    void applyNexusIdentity_appliesIdentityAndFiresApprovedEvent_whenApprovalPushed() {
        ApprovalPushRequest request = new ApprovalPushRequest();
        request.setAgentId(UUID.randomUUID());
        request.setApprovedName("ApprovedAgent");
        request.setAuthToken("auth-token");
        request.setPublicToken("public-token");
        request.setState(AgentState.APPROVED);

        agentService.applyNexusIdentity(request);

        assertThat(agentService.getAgentName()).isEqualTo("ApprovedAgent");
        assertThat(agentService.getState()).isEqualTo(AgentState.APPROVED);
        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    @Test
    @DisplayName("skips reset when agent is already in clean UNREGISTERED state")
    void forceReset_skipsReset_whenAlreadyCleanUnregistered() {
        Agent agent = buildAgent(AgentState.UNREGISTERED);
        agent.setId(null);
        ReflectionTestUtils.setField(agentService, "agent", agent);

        agentService.forceReset();

        assertThat(agentService.getState()).isEqualTo(AgentState.UNREGISTERED);
        assertThat(agentService.getAgentId()).isNull();
    }

    @Test
    @DisplayName("fires AgentApprovedEvent when agent transitions to APPROVED for first time")
    void applyNexusIdentity_firesApprovedEvent_whenFirstTimeApproved() {
        ReflectionTestUtils.setField(agentService, "agent", buildAgent(AgentState.PENDING_APPROVAL));

        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(UUID.randomUUID())
                .agentName("ApprovedAgent")
                .state(AgentState.APPROVED)
                .authToken("auth-token")
                .publicToken("public-token")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher).publishEvent(any(AgentApprovedEvent.class));
    }

    @Test
    @DisplayName("fires AgentIdentityChangedEvent when already approved agent identity changes")
    void applyNexusIdentity_firesIdentityChangedEvent_whenAlreadyApprovedAndIdentityChanges() {
        UUID agentId = UUID.randomUUID();
        Agent agent = buildAgent(AgentState.APPROVED);
        agent.setId(agentId);
        agent.setAuthToken("old-token");
        agent.setPublicToken("old-public-token");
        ReflectionTestUtils.setField(agentService, "agent", agent);

        AgentIdentityResponse response = AgentIdentityResponse.builder()
                .agentId(agentId)
                .agentName("RenamedAgent")
                .state(AgentState.APPROVED)
                .authToken("new-token")
                .publicToken("new-public-token")
                .build();

        agentService.applyNexusIdentity(response);

        verify(eventPublisher).publishEvent(any(AgentIdentityChangedEvent.class));
    }

    @Test
    @DisplayName("stores both tokens on the agent")
    void storeTokens_storesBothTokens() {
        agentService.storeTokens("auth-token", "public-token");

        assertThat(agentService.getAuthToken()).isEqualTo("auth-token");
        assertThat(agentService.getPublicToken()).isEqualTo("public-token");
    }

    private Agent buildAgent(AgentState state) {
        Agent agent = new Agent();
        agent.setAgentName("TestAgent");
        agent.setIpAddress("192.168.1.1");
        agent.setPort(8080);
        agent.setState(state);
        return agent;
    }
}