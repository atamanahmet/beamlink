package com.atamanahmet.beamlink.agent.repository;

import com.atamanahmet.beamlink.agent.domain.BatchTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BatchTransferRepository extends JpaRepository<BatchTransfer, UUID> {
    Optional<BatchTransfer> findByBatchTransferId(UUID batchTransferId);
}
