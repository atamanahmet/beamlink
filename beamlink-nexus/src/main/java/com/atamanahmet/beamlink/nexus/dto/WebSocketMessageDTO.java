package com.atamanahmet.beamlink.nexus.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper for all WebSocket messages
 * @param <T> Payload type (ApprovalPushRequest, AgentStatusDTO, List<TransferLog> etc.)
 */
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessageDTO<T> {

    private String type;

    private T payload;

    private Long version;


}