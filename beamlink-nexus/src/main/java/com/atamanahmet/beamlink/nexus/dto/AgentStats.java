package com.atamanahmet.beamlink.nexus.dto;

public record AgentStats(
        long total,
        long online,
        long offline,
        long pending,
        long pendingRename
) {}