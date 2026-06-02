package com.atamanahmet.beamlink.agent.repository;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.domain.enums.TransferSyncState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferLogRepository extends JpaRepository<TransferLog, UUID> {

    List<TransferLog> findBySyncState(TransferSyncState syncState);
}