package com.atamanahmet.beamlink.agent.dto;

import java.util.List;

public record StatusUpdateResponse(
        String status,
        String authToken,
        String publicToken,
        long peerVersion,
        boolean peerOutdated,
        List<AgentStatusDTO> agents,
        String approvedName  // null = no rename
) {
    public static StatusUpdateResponse pending() {
        return new StatusUpdateResponse(
                "pending_approval", null, null, 0, false, List.of(), null
        );
    }
}
