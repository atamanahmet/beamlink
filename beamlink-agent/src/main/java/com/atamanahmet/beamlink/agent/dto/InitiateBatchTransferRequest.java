package com.atamanahmet.beamlink.agent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateBatchTransferRequest {

    private List<String> filePaths;
    private UUID targetAgentId;
    private String targetIp;
    private int targetPort;
    private String targetToken;
}