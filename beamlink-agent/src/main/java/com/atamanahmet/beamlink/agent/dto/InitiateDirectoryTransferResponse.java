package com.atamanahmet.beamlink.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class InitiateDirectoryTransferResponse {
    private UUID directoryTransferId;
}