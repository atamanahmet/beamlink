package com.atamanahmet.beamlink.agent.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRenameRequest {
    private String name;
}
