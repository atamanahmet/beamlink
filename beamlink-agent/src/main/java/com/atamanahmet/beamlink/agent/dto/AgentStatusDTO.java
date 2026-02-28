package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Builder
public class AgentStatusDTO {

    private UUID agentId;
    private String agentName;
    private String ipAddress;
    private int port;
    private boolean status;
    private int unSyncedLogs;
    private long peerVersion;
    private String publicToken;
}
