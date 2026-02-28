package com.atamanahmet.beamlink.agent.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;import java.util.UUID;

/**
 * Represents a file transfer event
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransferLog {

    private String id;
    private UUID fromAgentId;
    private String fromAgentName;
    private UUID toAgentId;
    private String toAgentName;
    private String filename;
    private long fileSize;
    private Instant timestamp = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private boolean syncedToNexus = false;

}