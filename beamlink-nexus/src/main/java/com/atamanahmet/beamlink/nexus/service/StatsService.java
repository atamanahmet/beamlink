package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.domain.AgentRegistry;
import com.atamanahmet.beamlink.nexus.domain.NexusStats;
import com.atamanahmet.beamlink.nexus.domain.TransferLog;
import com.atamanahmet.beamlink.nexus.repository.DataStore;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Calculates statistics for dashboard
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final DataStore dataStore;

    /**
     * Get current nexus statistics
     */
    public NexusStats getStats() {

        List<AgentRegistry> agents = dataStore.getAllAgents();
        List<TransferLog> logs = dataStore.getAllTransferLogs();

        int totalAgents = agents.size();
        int onlineAgents = (int) agents.stream().filter(AgentRegistry::isOnline).count();
        int offlineAgents = totalAgents - onlineAgents;
        int pendingApprovals = dataStore.getAllPendingAgents().size();
        int pendingRenameApprovals = dataStore.getAllPendingRenames().size();
        int totalTransfers = logs.size();
        long totalDataTransferred = logs.stream().mapToLong(TransferLog::getFileSize).sum();

        return new NexusStats(
                totalAgents,
                onlineAgents,
                offlineAgents,
                pendingApprovals,
                pendingRenameApprovals,
                totalTransfers,
                totalDataTransferred
        );
    }
}