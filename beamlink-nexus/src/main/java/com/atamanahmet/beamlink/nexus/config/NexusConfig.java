package com.atamanahmet.beamlink.nexus.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Nexus configuration
 */
@Configuration
@Getter
public class NexusConfig {
    @Value("${nexus.upload.directory}")
    private String uploadDirectory;

    @Value("${nexus.upload.partial-directory:./data/partial}")
    private String partialDirectory;

    @Value("${nexus.transfer.expiry-hours}")
    private long transferExpiryHours;

    @Value("${server.port}")
    private int nexusPort;

    @Value("${nexus.ip-address}")
    private String ipAddress;

    @Value("${nexus.name}")
    private String name;

    @Value("${nexus.admin.username}")
    private String adminUsername;

    @Value("${nexus.admin.password}")
    private String adminPassword;

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
