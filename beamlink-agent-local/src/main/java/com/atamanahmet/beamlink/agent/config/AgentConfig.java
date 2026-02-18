package com.atamanahmet.beamlink.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.File;

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

    @Value("${server.port}")
    private int port;

    @PostConstruct
    public void init() {

        // Create upload directory if it doesn't exist
        File uploadDir = new File(uploadDirectory);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
            System.out.println("Created upload directory: " + uploadDir.getAbsolutePath());
        }
    }


}