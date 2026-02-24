package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.dto.NexusStats;

import com.atamanahmet.beamlink.nexus.dto.AgentStats;
import com.atamanahmet.beamlink.nexus.dto.TransferStats;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

/**
 * Calculates statistics for dashboard
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final AgentService agentService;
    private final TransferLogService transferLogService;

    /**
     * Get current nexus statistics
     */
    public NexusStats getStats() {

        AgentStats agentStats = agentService.getAgentStats();
        TransferStats transferStats = transferLogService.getTransferStats();

        return new NexusStats(
                agentStats,
                transferStats
        );
    }
}