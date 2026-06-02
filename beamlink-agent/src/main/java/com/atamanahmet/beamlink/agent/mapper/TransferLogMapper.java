package com.atamanahmet.beamlink.agent.mapper;

import com.atamanahmet.beamlink.agent.domain.TransferLog;
import com.atamanahmet.beamlink.agent.domain.enums.TransferSyncState;
import com.atamanahmet.beamlink.agent.dto.TransferLogDTO;

import java.time.Instant;
import java.util.UUID;

public final class TransferLogMapper {

    private TransferLogMapper() {}

    /** Creates a new entity from incoming transfer data, ready to persist. */
    public static TransferLog toEntity(TransferLogDTO dto) {
        return TransferLog.builder()
                .id(dto.getId() != null ? dto.getId() : UUID.randomUUID())
                .fromAgentId(dto.getFromAgentId())
                .fromAgentName(dto.getFromAgentName())
                .toAgentId(dto.getToAgentId())
                .toAgentName(dto.getToAgentName())
                .filename(dto.getFilename())
                .fileSize(dto.getFileSize())
                .averageSpeedMbps(dto.getAverageSpeedMbps())
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now())
                .syncState(TransferSyncState.PENDING)
                .build();
    }

    /** Maps entity to wire DTO for sending to Nexus. */
    public static TransferLogDTO toDTO(TransferLog entity) {
        return TransferLogDTO.builder()
                .id(entity.getId())
                .fromAgentId(entity.getFromAgentId())
                .fromAgentName(entity.getFromAgentName())
                .toAgentId(entity.getToAgentId())
                .toAgentName(entity.getToAgentName())
                .filename(entity.getFilename())
                .fileSize(entity.getFileSize())
                .averageSpeedMbps(entity.getAverageSpeedMbps())
                .timestamp(entity.getTimestamp())
                .build();
    }
}