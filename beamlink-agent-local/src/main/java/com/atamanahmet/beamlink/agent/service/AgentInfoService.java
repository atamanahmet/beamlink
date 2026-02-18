package com.atamanahmet.beamlink.agent.service;

import com.atamanahmet.beamlink.agent.domain.AgentInfo;
import com.atamanahmet.beamlink.agent.config.AgentConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentInfoService {

    private static final String INFO_FILE = "agent_info.json";

    private final AgentConfig config;

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    private AgentInfo agentInfo;

    @PostConstruct
    public void init() {
        File file = new File(INFO_FILE);

        if (file.exists()) {
            loadFromFile(file);
        } else {
            createNewAgentInfo(file);
        }
    }

    private void createNewAgentInfo(File file) {

        String newId = UUID.randomUUID().toString();

        String defaultName = "Agent-"+newId;

        agentInfo = new AgentInfo(newId, defaultName);

        saveToFile();

        System.out.println("Generated new agent identity: " + newId);
    }

    private void loadFromFile(File file) {
        try (FileReader reader = new FileReader(file)) {
            agentInfo = gson.fromJson(reader, AgentInfo.class);

            if (agentInfo == null || agentInfo.getAgentId() == null) {
                createNewAgentInfo(file);
            }

        } catch (Exception e) {
            createNewAgentInfo(file);
        }
    }

    public synchronized void updateAgentName(String newName) {
        agentInfo.setAgentName(newName);
        saveToFile();
    }

    public String getAgentId() {
        return agentInfo.getAgentId();
    }

    public String getAgentName() {
        return agentInfo.getAgentName();
    }

    private synchronized void saveToFile() {
        try (FileWriter writer = new FileWriter(INFO_FILE)) {
            gson.toJson(agentInfo, writer);
        } catch (IOException e) {
            System.err.println("Failed to save agent_info.json: " + e.getMessage());
        }
    }
}
