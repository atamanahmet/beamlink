package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.enums.TransferType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class DispatchResultItem {
    private UUID id;
    private TransferType type;
    private UUID dispatchId;
}