package com.atamanahmet.beamlink.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Peer representation (for peer list)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Peer {

    private String agentId;
    private String agentName;
    private String ipAddress;
    private int port;
    private boolean online;
}