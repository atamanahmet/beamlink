package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(locations = "classpath:application.properties")
class AgentRepositoryTest {

    @Autowired
    private AgentRepository agentRepository;

    private Agent buildAgent(String ip, int port, AgentState state) {
        return Agent.builder()
                .name("TestAgent-" + ip)
                .ipAddress(ip)
                .port(port)
                .state(state)
                .registeredAt(Instant.now())
                .lastSeenAt(Instant.now())
                .build();
    }

//    @Test
//    @DisplayName("context loads")
//    void contextLoads() {
//    }

    @Test
    @DisplayName("finds agent by ip address and port")
    void findByIpAddressAndPort_returnsAgent_whenExists() {
        Agent agent = buildAgent("192.168.1.10", 9090, AgentState.PENDING_APPROVAL);
        agentRepository.save(agent);

        Optional<Agent> result = agentRepository.findByIpAddressAndPort("192.168.1.10", 9090);

        assertThat(result).isPresent();
        assertThat(result.get().getIpAddress()).isEqualTo("192.168.1.10");
        assertThat(result.get().getPort()).isEqualTo(9090);
    }

    @Test
    @DisplayName("finds all agents by state")
    void findByState_returnsMatchingAgents() {
        agentRepository.save(buildAgent("192.168.1.10", 9090, AgentState.PENDING_APPROVAL));
        agentRepository.save(buildAgent("192.168.1.11", 9091, AgentState.APPROVED));
        agentRepository.save(buildAgent("192.168.1.12", 9092, AgentState.PENDING_APPROVAL));

        List<Agent> pending = agentRepository.findByState(AgentState.PENDING_APPROVAL);

        assertThat(pending).hasSize(2);
        assertThat(pending).allMatch(a -> a.getState() == AgentState.PENDING_APPROVAL);
    }

    @Test
    @DisplayName("marks agent approval as pushed")
    void markApprovalPushed_setsApprovalPushedToTrue() {
        Agent agent = buildAgent("192.168.1.10", 9090, AgentState.APPROVED);
        Agent saved = agentRepository.save(agent);

        agentRepository.markApprovalPushed(saved.getId());

        Agent updated = agentRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.isApprovalPushed()).isTrue();
    }

    @Test
    @DisplayName("returns true when agent name already exists")
    void existsByName_returnsTrue_whenNameExists() {
        agentRepository.save(buildAgent("192.168.1.10", 9090, AgentState.APPROVED));

        boolean exists = agentRepository.existsByName("TestAgent-192.168.1.10");

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("finds agents with pending rename requests")
    void findByStateAndRequestedNameIsNotNull_returnsPendingRenames() {
        Agent agent = buildAgent("192.168.1.10", 9090, AgentState.APPROVED);
        agent.setRequestedName("NewName");
        agentRepository.save(agent);
        agentRepository.save(buildAgent("192.168.1.11", 9091, AgentState.APPROVED));

        List<Agent> pending = agentRepository.findByStateAndRequestedNameIsNotNull(AgentState.APPROVED);

        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getRequestedName()).isEqualTo("NewName");
    }
}