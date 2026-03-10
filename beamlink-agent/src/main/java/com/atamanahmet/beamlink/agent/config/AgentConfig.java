package com.atamanahmet.beamlink.agent.config;

import com.atamanahmet.beamlink.agent.domain.Agent;
import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.dto.AgentIdentityResponse;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationRequest;
import com.atamanahmet.beamlink.agent.dto.AgentRegistrationResponse;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import com.atamanahmet.beamlink.agent.event.NexusLostAgentEvent;
import com.atamanahmet.beamlink.agent.event.NexusOfflineEvent;
import com.atamanahmet.beamlink.agent.event.NexusOnlineEvent;
import com.atamanahmet.beamlink.agent.service.AgentService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Value("${agent.ip-address}")
    private String ipAddress;

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



