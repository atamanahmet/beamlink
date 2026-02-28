package com.atamanahmet.beamlink.nexus.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Nexus configuration
 */
@Configuration
@Getter
public class NexusConfig {
    @Value("${nexus.upload.directory}")
    private String uploadDirectory;

    @Value("${server.port}")
    private int nexusPort;


    @Value("${nexus.ip-address}")
    private String ipAddress;
}
