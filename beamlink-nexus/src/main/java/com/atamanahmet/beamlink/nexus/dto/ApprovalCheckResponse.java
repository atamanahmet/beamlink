package com.atamanahmet.beamlink.nexus.dto;

import lombok.Data;

/**
 * Response for approval check
 */
@Data
public class ApprovalCheckResponse {
    private String status;       // "pending" or "approved" TODO: make enum
    private String authToken;
    private String publicToken;
}
