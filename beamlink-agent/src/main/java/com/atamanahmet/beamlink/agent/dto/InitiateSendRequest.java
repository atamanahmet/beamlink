package com.atamanahmet.beamlink.agent.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class InitiateSendRequest {
    private List<String> paths;
    private UUID targetAgentId;
    private String targetIp;
    private int targetPort;
}