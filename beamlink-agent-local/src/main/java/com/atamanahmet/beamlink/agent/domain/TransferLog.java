package com.atamanahmet.beamlink.agent.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Represents a file transfer event
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferLog {

    private String id;
    private String fromAgentId;
    private String fromAgentName;
    private String toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private LocalDateTime timestamp=LocalDateTime.now();
    private boolean syncedToNexus = false;

}