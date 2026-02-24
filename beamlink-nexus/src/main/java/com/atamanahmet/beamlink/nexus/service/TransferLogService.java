package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.dto.LogSyncRequest;
import com.atamanahmet.beamlink.nexus.dto.TransferStats;
import com.atamanahmet.beamlink.nexus.repository.TransferLogRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferLogService {

    private final Logger log = LoggerFactory.getLogger(TransferLogService.class);

    private final TransferLogRepository transferLogRepository;

    @Transactional
    public List<UUID> sync(UUID agentId, List<LogSyncRequest> incoming) {

        List<TransferLog> toSave = incoming.stream()
                .filter(r -> r.getId() != null)
                .filter(r -> !transferLogRepository.existsById(r.getId()))
                .map(r -> TransferLog.builder()
                        .id(r.getId())
                        .fromAgentId(agentId)
                        .fromAgentName(r.getFromAgentName())
                        .toAgentId(r.getToAgentId())
                        .toAgentName(r.getToAgentName())
                        .filename(r.getFilename())
                        .fileSize(r.getFileSize())
                        .timestamp(r.getTimestamp() != null ? r.getTimestamp() : Instant.now())
                        .build())
                .collect(Collectors.toList());

        if (!toSave.isEmpty()) {
            transferLogRepository.saveAll(toSave);
            log.info("Synced {}/{} new logs from agent {}.", toSave.size(), incoming.size(), agentId);
        }

        return incoming.stream()
                .map(LogSyncRequest::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void logTransfer(UUID fromAgentId, String fromAgentName, String filename, long fileSize) {
        TransferLog entry = TransferLog.builder()
                .id(UUID.randomUUID())
                .fromAgentId(fromAgentId)
                .fromAgentName(fromAgentName)
                .filename(filename)
                .fileSize(fileSize)
                .timestamp(Instant.now())
                .build();

        transferLogRepository.save(entry);
    }

    public List<TransferLog> getLogs(Pageable pageable) {

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "timestamp")
        );

        return transferLogRepository.findAll(sorted).getContent();
    }

    public List<TransferLog> getRecentLogs(int limit) {

        return getLogs(PageRequest.of(0, limit));
    }

    public TransferStats getTransferStats() {

        long totalTransfers = transferLogRepository.count();

        Long totalSize = transferLogRepository.sumFileSize();
        long totalDataTransferred = totalSize != null ? totalSize : 0L;

        return new TransferStats(
                totalTransfers,
                totalDataTransferred
        );
    }
}