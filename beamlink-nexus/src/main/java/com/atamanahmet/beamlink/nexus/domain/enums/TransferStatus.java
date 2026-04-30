package com.atamanahmet.beamlink.nexus.domain.enums;

public enum TransferStatus {
    PENDING,    // initiated, not yet sending
    ACTIVE,     // chunks are flowing
    PAUSED,     // explicitly paused by user
    COMPLETED,  // all chunks received and verified
    CANCELLED,  // explicitly cancelled by user
    FAILED,     // retries exhausted
    EXPIRED     // abandoned, cleanup job will delete partial file
}