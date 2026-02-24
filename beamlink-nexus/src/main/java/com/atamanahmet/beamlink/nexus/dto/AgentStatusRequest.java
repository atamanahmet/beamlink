package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class AgentStatusRequest {
    private UUID agentId;
    private String agentName;
    private String ipAddress;
    private int port;
    private boolean online;
    private int unSyncedLogs;
    private long peerVersion;
}