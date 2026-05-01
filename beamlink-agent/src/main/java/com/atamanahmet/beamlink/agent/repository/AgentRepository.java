package com.atamanahmet.beamlink.agent.repository;

import com.atamanahmet.beamlink.agent.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentRepository extends JpaRepository<Agent, Long> {}