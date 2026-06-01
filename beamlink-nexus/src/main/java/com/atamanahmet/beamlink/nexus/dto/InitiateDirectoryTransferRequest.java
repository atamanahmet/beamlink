package com.atamanahmet.beamlink.nexus.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateDirectoryTransferRequest {

    private String sourcePath;
    private UUID dispatchId;
    private UUID targetAgentId;
    private String targetIp;
    private int targetPort;
}