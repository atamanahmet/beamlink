package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LogService {

    private static final String LOG_FILE = "transfer_log.json";

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    private List<TransferLog> logs = new ArrayList<>();

    public LogService() {
        loadLogs();
    }

    /**
     * Log a file transfer
     */
    public synchronized  void logTransfer(TransferLog log) {
        log.setId(UUID.randomUUID().toString());
        log.setTimestamp(LocalDateTime.now());
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
    public synchronized  List<TransferLog> getUnsyncedLogs() {
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

        try (FileReader reader = new FileReader(logFile)) {
            Type listType = new TypeToken<List<TransferLog>>(){}.getType();
            logs = gson.fromJson(reader, listType);

            if (logs == null) {
                logs = new ArrayList<>();
            }

        } catch (IOException e) {
            System.err.println("Error loading transfer logs: " + e.getMessage());
            logs = new ArrayList<>();
        }
    }

    /**
     * Save logs to file
     */
    private void saveLogs() {
        try (FileWriter writer = new FileWriter(LOG_FILE)) {
            gson.toJson(logs, writer);
        } catch (IOException e) {
            System.err.println("Error saving transfer logs: " + e.getMessage());
        }
    }
}