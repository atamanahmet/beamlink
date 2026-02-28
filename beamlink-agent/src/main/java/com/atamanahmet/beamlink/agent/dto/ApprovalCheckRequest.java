package com.atamanahmet.beamlink.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Request DTO for approval check
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ApprovalCheckRequest {
    private UUID agentId;
}