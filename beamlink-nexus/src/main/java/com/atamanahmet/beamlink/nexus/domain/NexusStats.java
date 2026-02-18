package com.atamanahmet.beamlink.nexus.domain;

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

    private int totalAgents;
    private int onlineAgents;
    private int offlineAgents;
    private int pendingApprovals;
    private int pendingRenameApprovals;
    private int totalTransfers;
    private long totalDataTransferred;
}