package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LogService {

    private static final String LOG_FILE = "transfer_log.json";

    private final ObjectMapper objectMapper;

    private List<TransferLog> logs = new ArrayList<>();

    public LogService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        loadLogs();
    }

    /**
     * Log a file transfer
     */
    public synchronized void logTransfer(TransferLog log) {
        log.setId(UUID.randomUUID().toString());
        log.setTimestamp(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        log.setSyncedToNexus(false);

        logs.add(log);
        saveLogs();
    }

    /**
     * Get all transfer logs
     */
    public List<TransferLog> getAllLogs() {
        return new ArrayList<>(logs);
    }

    /**
     * Get unsynced logs (for sending to nexus)
     */
    public synchronized List<TransferLog> getUnsyncedLogs() {
        return logs.stream()
                .filter(log -> !log.isSyncedToNexus())
                .toList();
    }

    /**
     * Mark logs as synced
     */
    public synchronized void markAsSynced(List<String> logIds) {
        logs.removeIf(log -> logIds.contains(log.getId()));
        saveLogs();
    }

    /**
     * Load logs from file
     */
    private void loadLogs() {
        File logFile = new File(LOG_FILE);
        if (!logFile.exists()) {
            return;
        }

        try {
            logs = objectMapper.readValue(logFile, new TypeReference<List<TransferLog>>() {});
            if (logs == null) {
                logs = new ArrayList<>();
            }
        } catch (Exception e) {
            System.err.println("Error loading transfer logs: " + e.getMessage());
            logs = new ArrayList<>();
        }
    }

    /**
     * Save logs to file
     */
    private void saveLogs() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(LOG_FILE), logs);
        } catch (Exception e) {
            System.err.println("Error saving transfer logs: " + e.getMessage());
        }
    }
}