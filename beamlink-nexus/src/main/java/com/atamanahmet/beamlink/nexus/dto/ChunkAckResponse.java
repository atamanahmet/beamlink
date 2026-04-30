package com.atamanahmet.beamlink.nexus.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ChunkAckResponse {

    private final long confirmedOffset;
    private final boolean complete;

    @JsonCreator
    public ChunkAckResponse(
            @JsonProperty("confirmedOffset") long confirmedOffset,
            @JsonProperty("complete") boolean complete) {

        this.confirmedOffset = confirmedOffset;
        this.complete = complete;
    }
}