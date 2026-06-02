package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalPushRequest {
    private UUID agentId;
    private String authToken;
    private String publicToken;
    private UUID publicId;
    private String approvedName;
    private AgentState state;
    private String nexusPublicKey;
}