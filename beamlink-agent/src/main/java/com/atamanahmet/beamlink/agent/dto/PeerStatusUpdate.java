package com.atamanahmet.beamlink.agent.dto;

import lombok.Data;

@Data
public class PeerStatusUpdate {
    private String agentId;
    private boolean online;
}
