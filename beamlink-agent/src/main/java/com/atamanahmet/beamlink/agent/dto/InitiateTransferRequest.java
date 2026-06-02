package com.atamanahmet.beamlink.agent.dto;

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
}