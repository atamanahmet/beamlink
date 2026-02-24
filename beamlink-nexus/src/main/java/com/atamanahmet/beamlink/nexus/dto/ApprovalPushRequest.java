package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalPushRequest {
    private UUID agentId;
    private String authToken;
    private String publicToken;
    private String approvedName;
    private AgentState state;
}