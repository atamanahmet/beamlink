package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.enums.TransferType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class TransferSummary {

    private UUID id;
    private TransferType type;
    private UUID dispatchId;
    private String name;
    private String status;
    private long totalSize;
    private long confirmedBytes;
    private int totalFiles;
    private UUID targetAgentId;
    private String targetIp;
    private Integer targetPort;
    private Instant createdAt;
    private Instant completedAt;
    private String failureReason;
    private long activeTransferMs;
}