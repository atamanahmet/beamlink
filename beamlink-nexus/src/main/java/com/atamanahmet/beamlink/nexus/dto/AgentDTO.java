package com.atamanahmet.beamlink.nexus.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AgentDTO {

    private UUID id;
    private String agentName;
    private String ipAddress;
    private int port;
    private boolean online;
    private UUID publicId;
}