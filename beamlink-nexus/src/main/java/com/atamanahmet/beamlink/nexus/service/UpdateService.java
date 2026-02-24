package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateService {

    private final AgentService agentService;

    private static final String PACKAGE_NAME = "update.zip";

    private Path getStorageDir() {
        return Paths.get("./update-package").toAbsolutePath().normalize();
    }

    public void storePackage(MultipartFile file) {
        try {
            Path dir = getStorageDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(PACKAGE_NAME);
            Files.write(target, file.getBytes());
            log.info("Update package stored at {}", target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store update package", e);
        }
    }

    public void pushToAgent(UUID agentId) {
        Agent agent = agentService.findByAgentId(agentId);
        sendPackage(agent);
    }

    public void pushToAllOnline() {
        agentService.getOnlineAgents().forEach(this::sendPackage);
    }

    private void sendPackage(Agent agent) {
        try {
            Path packagePath = getStorageDir().resolve(PACKAGE_NAME);
            if (!Files.exists(packagePath)) {
                throw new RuntimeException("No update package found on Nexus");
            }

            byte[] fileBytes = Files.readAllBytes(packagePath);
            String url = "http://" + agent.getIpAddress() + ":" + agent.getPort() + "/api/update/receive";

            RestTemplate restTemplate = new RestTemplate();

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Auth-Token", agent.getAuthToken());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> entity = new HttpEntity<>(fileBytes, headers);
            restTemplate.postForEntity(url, entity, Void.class);

            log.info("Update package pushed to agent {} at {}", agent.getId(), agent.getIpAddress());
        } catch (Exception e) {
            log.error("Failed to push update to agent {}: {}", agent.getId(), e.getMessage());
            throw new RuntimeException("Push failed", e);
        }
    }

}
