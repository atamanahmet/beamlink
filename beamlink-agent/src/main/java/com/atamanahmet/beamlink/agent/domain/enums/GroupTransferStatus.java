package com.atamanahmet.beamlink.agent.domain.enums;

public enum GroupTransferStatus {
    PENDING,      // registered, not yet sending
    ACTIVE,       // files are flowing
    PARTIAL,      // some files failed or cancelled, others completed
    PAUSED,       // active file paused, queued files PENDING
    COMPLETED,    // all files transferred successfully
    CANCELLED,    // explicitly cancelled by user
    FAILED,       // unrecoverable error
    EXPIRED       // abandoned, cleanup job will handle partial files
}