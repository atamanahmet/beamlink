package com.atamanahmet.beamlink.agent;

import com.atamanahmet.beamlink.agent.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for Beamlink Agent
 * Runs on each PC in the network
 */
@SpringBootApplication
@EnableScheduling
@RequiredArgsConstructor
public class BeamlinkAgentApplication implements CommandLineRunner {

    private final RegistrationService registrationService;

    public static void main(String[] args) {
        SpringApplication.run(BeamlinkAgentApplication.class, args);
    }

    @Override
    public void run(String... args) {
        System.out.println("========================================");
        System.out.println("BEAMLINK AGENT STARTED");
        System.out.println("========================================");
        System.out.println("Agent UI: http://localhost:8081");
        System.out.println("========================================");

        // Register with nexus on startup
        registrationService.registerWithNexus();
    }
}