package com.atamanahmet.beamlink.agent.config;

import lombok.Getter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

import java.io.File;
import java.net.http.HttpClient;


/**
 * Configuration class for agent settings
 */
@Configuration
@Getter
public class AgentConfig {

    @Value("${agent.nexus.url}")
    private String nexusUrl;

    @Value("${agent.upload.directory}")
    private String uploadDirectory;

    @Value("${agent.upload.partial-directory:./data/partial}")
    private String partialDirectory;

    @Value("${server.port}")
    private int port;

    @Value("${agent.ip-address}")
    private String ipAddress;

    @Value("${agent.transfer.expiry-hours}")
    private long transferExpiryHours;

    @Value("${agent.auto-resume-group-transfers:false}")
    private boolean autoResumeGroupTransfers;

    @PostConstruct
    public void init() {

        // Create upload directory if it doesn't exist
        File uploadDir = new File(uploadDirectory);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        new File(uploadDirectory).mkdirs();
        new File(partialDirectory).mkdirs();
        new File("./data/database").mkdirs();
    }

}



