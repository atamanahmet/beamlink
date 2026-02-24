package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface TransferLogRepository extends JpaRepository<TransferLog, UUID> {
    @Query("SELECT COALESCE(SUM(t.fileSize), 0) FROM TransferLog t")
    Long sumFileSize();
}