package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.repository.DataStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LogService {

    private final DataStore dataStore;
    private final Logger log = LoggerFactory.getLogger(LogService.class);

    /**
     * Log a file transfer (used by Nexus when receiving files)
     */
    public void logTransfer(TransferLog transferLog) {

        transferLog.setId(UUID.randomUUID().toString());
        transferLog.setTimestamp(LocalDateTime.now());

        dataStore.addTransferLog(transferLog);

        log.info("📋 Logged transfer {}", transferLog.getId());
    }

    /**
     * Get all transfer logs
     */
    public List<TransferLog> getAllLogs() {
        return dataStore.getAllTransferLogs();
    }

    /**
     * Get recent logs
     */
    public List<TransferLog> getRecentLogs(int limit) {

        List<TransferLog> allLogs = dataStore.getAllTransferLogs();

        int size = allLogs.size();
        if (size <= limit) {
            return allLogs;
        }

        return allLogs.subList(size - limit, size);
    }

    /**
     * Merge logs from agents into nexus logs
     */
    public synchronized List<String> mergeLogs(List<TransferLog> agentLogs) {

        List<TransferLog> existingLogs = dataStore.getAllTransferLogs();

        // O(1) lookup
        Set<String> existingIds = existingLogs.stream()
                .map(TransferLog::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> mergedIds = new ArrayList<>();

        for (TransferLog agentLog : agentLogs) {

            if (agentLog.getId() == null) {
                continue; // skip invalid logs
            }

            if (!existingIds.contains(agentLog.getId())) {

                dataStore.addTransferLog(agentLog);
                mergedIds.add(agentLog.getId());
                existingIds.add(agentLog.getId());
            } else {
                log.debug("Log {} already exists, skipping", agentLog.getId());
            }
        }

        if (!mergedIds.isEmpty()) {
            log.info("📋 Merged {} new logs from agent", mergedIds.size());
        }

        return mergedIds;
    }
}
