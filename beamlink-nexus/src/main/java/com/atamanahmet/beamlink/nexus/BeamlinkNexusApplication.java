package com.atamanahmet.beamlink.nexus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class BeamlinkNexusApplication {

    public static void main(String[] args) {

        var ctx = SpringApplication.run(BeamlinkNexusApplication.class, args);

        String port = ctx.getEnvironment().getProperty("server.port");
        String ipAddress = ctx.getEnvironment().getProperty("nexus.ip-address");

        System.out.println("==================================");
        System.out.println("🌐 BEAMLINK NEXUS STARTED");
        System.out.println("==================================");
        System.out.println("Dashboard: http://" + ipAddress + ":" + port);
        System.out.println("API: http://" + ipAddress + ":" + port);
        System.out.println("==================================");
    }
}