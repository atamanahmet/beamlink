package com.atamanahmet.beamlink.nexus.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents a registered agent in the network
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentRegistry {

    private String agentId;
    private String name;
    private String ipAddress;
    private int port;
    private boolean online;
    private boolean approved;
    private LocalDateTime lastSeen;
    private LocalDateTime registeredAt;
    private int fileCount;

    public String getAddress() {
        return ipAddress + ":" + port;
    }
}