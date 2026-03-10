package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID>{
    List<Agent> findByState(AgentState state);

    //not approved agents
    List<Agent> findByStateAndApprovalPushedFalse(AgentState state);

    //online agents
    List<Agent> findByLastSeenAtAfter(Instant threshold);

    //offline agents
    List<Agent> findByLastSeenAtBefore(Instant threshold);

    //agents pending rename approval
    List<Agent> findByStateAndRequestedNameIsNotNull(AgentState state);

    Optional<Agent> findByPublicId(UUID publicId);

    Optional<Agent> findByIpAddressAndPort(String ipAddress, int port);

    boolean existsByName(String agentName);

    long countByLastSeenAtAfter(Instant threshold);
    long countByLastSeenAtBefore(Instant threshold);
    long countByState(AgentState state);
    long countByStateAndRequestedNameIsNotNull(AgentState state);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Agent a SET a.approvalPushed = true WHERE a.id = :id")
    void markApprovalPushed(@Param("id") UUID id);
}
