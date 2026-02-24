package com.atamanahmet.beamlink.nexus.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Statistics for dashboard
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NexusStats {

    private AgentStats agentStats;
    private TransferStats transferStats;
}