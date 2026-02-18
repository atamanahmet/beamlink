package com.atamanahmet.beamlink.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an agent in the network
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Agent {

    private String agentId;
    private String name;
    private String ipAddress;
    private int port;
    private long lastSeen = System.currentTimeMillis();;
    private boolean online=true;

    public String getAddress() {
        return ipAddress + ":" + port;
    }
}