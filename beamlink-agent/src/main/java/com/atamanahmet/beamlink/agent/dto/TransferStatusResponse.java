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
    private String failureReason;
    private UUID targetAgentId;
    private Instant createdAt;
    private Instant lastChunkAt;
}