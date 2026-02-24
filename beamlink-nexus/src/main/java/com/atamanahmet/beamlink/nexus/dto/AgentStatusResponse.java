package com.atamanahmet.beamlink.nexus.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStatusResponse {
    private String status;
    private String approvedName;
    private boolean peerOutdated;
    private List<AgentDTO> agents;
}