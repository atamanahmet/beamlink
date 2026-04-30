package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateTransferRequest {
    private String filePath;
    private UUID targetAgentId;
    private String targetIp;
    private int targetPort;
    private String targetToken;     // auth token for target agent
}