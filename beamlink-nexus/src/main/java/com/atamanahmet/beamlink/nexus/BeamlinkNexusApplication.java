package com.atamanahmet.beamlink.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BeamlinkNexusApplication {

    public static void main(String[] args) {

        SpringApplication.run(BeamlinkNexusApplication.class, args);
        System.out.println("==================================");
        System.out.println("🌐 BEAMLINK NEXUS STARTED");
        System.out.println("==================================");
        System.out.println("Dashboard: http://localhost:5000");
        System.out.println("API: http://localhost:5000/api");
        System.out.println("==================================");
    }

}
