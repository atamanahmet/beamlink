package com.atamanahmet.beamlink.nexus.dto;

public record TransferStats(
        long totalTransfers,
        long totalDataTransferred
) {}