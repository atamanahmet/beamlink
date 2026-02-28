package com.atamanahmet.beamlink.agent;

import com.atamanahmet.beamlink.agent.domain.AgentState;
import com.atamanahmet.beamlink.agent.event.NexusEventPublisher;
import com.atamanahmet.beamlink.agent.service.AgentService;
import com.atamanahmet.beamlink.agent.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for Beamlink Agent
 * Runs on each PC in the network
 */
@SpringBootApplication(
        exclude = {
                org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class
        }
)
@EnableScheduling
@RequiredArgsConstructor
@EnableAsync
public class BeamlinkAgentApplication implements CommandLineRunner {

    @Value("${server.port}")
    private String port;

    @Value("${agent.ip-address}")
    private String ipAddress;

    private final Logger log = LoggerFactory.getLogger(BeamlinkAgentApplication.class);
    private final RegistrationService registrationService;


    public static void main(String[] args) {
        SpringApplication.run(BeamlinkAgentApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("========================================");
        System.out.println("BEAMLINK AGENT STARTED");
        System.out.println("========================================");
        System.out.println("Agent UI: http://"+ipAddress+":"+port);
        System.out.println("========================================");
        System.out.println("Update received");

        registrationService.resolveIdentityFromNexus();

    }
}