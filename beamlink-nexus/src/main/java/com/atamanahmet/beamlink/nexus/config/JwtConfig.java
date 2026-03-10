package com.atamanahmet.beamlink.nexus.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "nexus.jwt")
@Getter
@Setter
public class JwtConfig {
    private String secret;
    private long adminExpirationMinutes;
    private long agentAuthExpirationDays;
    private long agentPublicExpirationDays;
}