package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.AgentState;
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
    private String approvedName; // optional, if rename happens
    private AgentState state;
}