package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.enums.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TransferStatusResponse {
    private UUID transferId;
    private TransferStatus status;
    private long confirmedOffset;
    private long fileSize;
    private String fileName;
    private String failureReason;   // null unless FAILED
    private UUID targetAgentId;      // to match peer online/offline
    private Instant createdAt;       // for elapsed time
    private Instant lastChunkAt;     // for speed calculation
}