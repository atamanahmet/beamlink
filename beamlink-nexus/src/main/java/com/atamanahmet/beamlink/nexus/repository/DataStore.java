package com.atamanahmet.beamlink.nexus.repository;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.domain.PendingAgent;
import com.atamanahmet.beamlink.nexus.domain.PendingRename;
import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.service.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Getter
public class DataStore {

    private static final Logger log = LoggerFactory.getLogger(DataStore.class);

    private static final String DATA_DIR = "./data";
    private static final String AGENTS_FILE = DATA_DIR + "/agents.json";
    private static final String PENDING_FILE = DATA_DIR + "/pending_agents.json";
    private static final String PENDING_RENAMES_FILE = DATA_DIR + "/pending_renames.json";
    private static final String LOGS_FILE = DATA_DIR + "/transfer_logs.json";
    private static final String VERSION_FILE = DATA_DIR + "/peer_version.json";

    private LocalDateTime peerListLastModified = LocalDateTime.now();

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    // Version cached in memory
    private long peerListVersion = 1;

    // In memory
    private final Map<String, AgentRegistry> agents = new ConcurrentHashMap<>();
    private final Map<String, PendingAgent> pendingAgents = new ConcurrentHashMap<>();
    private final Map<String, PendingRename> pendingRenames = new ConcurrentHashMap<>();


    // Synchronized list for thread safety
    private final List<TransferLog> transferLogs =
            Collections.synchronizedList(new ArrayList<>());


    public DataStore() {
        ensureDataDirectory();
        loadData();
    }

    private void ensureDataDirectory() {
        try {
            Path path = Paths.get(DATA_DIR);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("Created data directory: {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }
    }

   // Agent management
    public void saveAgent(AgentRegistry agent) {
        agents.put(agent.getAgentId(), agent);
        saveAgentsToFile();
    }

    // Structural change
    public void saveAgentWithVersionIncrement(AgentRegistry agent) {
        agents.put(agent.getAgentId(), agent);
        saveAgentAndIncrementVersion();
    }

    public AgentRegistry getAgent(String agentId) {
        return agents.get(agentId);
    }

    public List<AgentRegistry> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    public void deleteAgent(String agentId) {
        agents.remove(agentId);
        saveAgentAndIncrementVersion();
    }

    private void loadAgents() {
        File file = new File(AGENTS_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, AgentRegistry>>(){}.getType();
            Map<String, AgentRegistry> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                agents.putAll(loaded);
                log.info("Loaded {} agents", agents.size());
            }

        } catch (IOException e) {
            log.error("Error loading agents", e);
        }
    }

    private synchronized void saveAgentsToFile() {

        try (FileWriter writer = new FileWriter(AGENTS_FILE)) {

            gson.toJson(agents, writer);


        } catch (IOException e) {
            log.error("Error saving agents", e);
        }
    }

    // For structural changes (with version increment)
    private synchronized void saveAgentAndIncrementVersion() {
        try (FileWriter writer = new FileWriter(AGENTS_FILE)) {
            gson.toJson(agents, writer);
            incrementPeerListVersion(); // Only if save succeeds!
        } catch (IOException e) {
            log.error("Error saving agents", e);
            // Version not incremented if save failed
        }
    }

    // Pending agent actions
    public void savePendingAgent(PendingAgent agent) {
        pendingAgents.put(agent.getAgentId(), agent);
        savePendingAgentsToFile();
    }

    public PendingAgent getPendingAgent(String agentId) {
        return pendingAgents.get(agentId);
    }

    public List<PendingAgent> getAllPendingAgents() {
        return new ArrayList<>(pendingAgents.values());
    }

    public void deletePendingAgent(String agentId) {

        pendingAgents.remove(agentId);

        savePendingAgentsToFile();
    }

    private void loadPendingAgents() {

        File file = new File(PENDING_FILE);

        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {

            Type type = new TypeToken<Map<String, PendingAgent>>(){}.getType();

            Map<String, PendingAgent> loaded = gson.fromJson(reader, type);

            if (loaded != null) {

                pendingAgents.putAll(loaded);

                log.info("Loaded {} pending agents", pendingAgents.size());
            }

        } catch (IOException e) {
            log.error("Error loading pending agents", e);
        }
    }

    private void loadPendingRenames() {
        File file = new File(PENDING_RENAMES_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {

            Type type = new TypeToken<Map<String, PendingRename>>(){}.getType();

            Map<String, PendingRename> loaded = gson.fromJson(reader, type);

            if (loaded != null) {

                pendingRenames.putAll(loaded);

                log.info("Loaded {} pending renames", pendingRenames.size());
            }

        } catch (IOException e) {
            log.error("Error loading pending renames", e);
        }
    }

    private synchronized void savePendingAgentsToFile() {

        try (FileWriter writer = new FileWriter(PENDING_FILE)) {

            gson.toJson(pendingAgents, writer);

        } catch (IOException e) {

            log.error("Error saving pending agents", e);
        }
    }

    private synchronized void savePendingRenamesToFile() {
        try (FileWriter writer = new FileWriter(PENDING_RENAMES_FILE)) {

            gson.toJson(pendingRenames, writer);

        } catch (IOException e) {

            log.error("Error saving pending renames", e);
        }
    }

    public void savePendingRename(PendingRename rename) {

        pendingRenames.put(rename.getAgentId(), rename);

        savePendingRenamesToFile();
    }

    public PendingRename getPendingRename(String agentId) {

        return pendingRenames.get(agentId);
    }

    public List<PendingRename> getAllPendingRenames() {

        return new ArrayList<>(pendingRenames.values());
    }

    public void deletePendingRename(String agentId) {

        pendingRenames.remove(agentId);

        savePendingRenamesToFile();
    }



    // Transfer logs
    public synchronized void addTransferLog(TransferLog transferLog) {

        transferLogs.add(transferLog);

        saveLogsToFile();
    }

    public List<TransferLog> getAllTransferLogs() {

        synchronized (transferLogs) {

            return new ArrayList<>(transferLogs);
        }
    }

    private void loadLogs() {
        File file = new File(LOGS_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<List<TransferLog>>(){}.getType();
            List<TransferLog> loaded = gson.fromJson(reader, type);

            if (loaded != null) {
                transferLogs.addAll(loaded);
                log.info("Loaded {} transfer logs", transferLogs.size());
            }

        } catch (IOException e) {
            log.error("Error loading transfer logs", e);
        }
    }

    private synchronized void saveLogsToFile() {
        try (FileWriter writer = new FileWriter(LOGS_FILE)) {
            gson.toJson(transferLogs, writer);
        } catch (IOException e) {
            log.error("Error saving transfer logs", e);
        }
    }

    /**
     * Peer list version file actions
     */
    private synchronized void incrementPeerListVersion() {
        peerListVersion++;
        saveVersion();
        log.debug("Peer list version: {}", peerListVersion);
    }

    private void saveVersion() {
        try (FileWriter writer = new FileWriter(VERSION_FILE)) {
            writer.write(String.valueOf(peerListVersion));
        } catch (IOException e) {
            log.error("Error saving version", e);
        }
    }

    private void loadVersion() {
        File file = new File(VERSION_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            char[] buffer = new char[32];
            int len = reader.read(buffer);
            peerListVersion = Long.parseLong(new String(buffer, 0, len).trim());
            log.info("Loaded peer list version: {}", peerListVersion);
        } catch (IOException | NumberFormatException e) {
            log.error("Error loading version", e);
            peerListVersion = 1;
        }
    }

    /**
     * Initial load
     */
    private void loadData() {
        loadAgents();
        loadPendingAgents();
        loadPendingRenames();
        loadLogs();
        loadVersion();
    }
}
