package com.atamanahmet.beamlink.agent.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Represents agent in the network
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Agent {

    private UUID id;

    private String agentName;

    private String ipAddress;

    private int port;

    private long lastSeen = System.currentTimeMillis();

    private boolean online=true;

    private String authToken;
    private String publicToken;

    private AgentState state = AgentState.UNREGISTERED;

    public boolean isApproved() {
        return state == AgentState.APPROVED;
    }

    public String getAddress() {
        return ipAddress + ":" + port;
    }

    public Peer toPeer() {
        return new Peer(
                id,
                agentName,
                ipAddress,
                port,
                lastSeen,
                true,
                publicToken
        );
    }
}