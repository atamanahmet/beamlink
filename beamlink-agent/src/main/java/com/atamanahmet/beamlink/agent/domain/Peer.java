package com.atamanahmet.beamlink.agent.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Peer representation (for peer list)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Peer {

    @JsonAlias("id")
    private UUID agentId;

    private String agentName;

    private String ipAddress;

    private int port;

    private long lastSeen = System.currentTimeMillis();

    private boolean online;

    private String publicToken;


    public String getAddress() {
        return ipAddress + ":" + port;
    }

}