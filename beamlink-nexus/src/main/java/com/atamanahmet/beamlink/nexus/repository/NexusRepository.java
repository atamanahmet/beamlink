package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.Nexus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NexusRepository extends JpaRepository<Nexus, Integer> {
}