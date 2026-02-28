package com.atamanahmet.beamlink.agent.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRegistrationRequest {
    private String agentName;
    private String ipAddress;
    private int port;
}