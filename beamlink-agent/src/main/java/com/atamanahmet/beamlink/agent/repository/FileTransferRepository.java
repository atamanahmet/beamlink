package com.atamanahmet.beamlink.agent.repository;

import com.atamanahmet.beamlink.agent.domain.FileTransfer;
import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileTransferRepository extends JpaRepository<FileTransfer, UUID> {

    Optional<FileTransfer> findByTransferId(UUID transferId);

    List<FileTransfer> findByStatus(TransferStatus status);

    List<FileTransfer> findByStatusAndExpiresAtBefore(TransferStatus status, Instant now);

    List<FileTransfer> findAllByOrderByCreatedAtDesc();

    List<FileTransfer> findByDirectoryTransferId(UUID directoryTransferId);

    List<FileTransfer> findByDirectoryTransferIdAndStatus(UUID directoryTransferId, TransferStatus status);

    List<FileTransfer> findByBatchTransferId(UUID batchTransferId);

    List<FileTransfer> findByBatchTransferIdAndStatus(UUID batchTransferId, TransferStatus status);
}