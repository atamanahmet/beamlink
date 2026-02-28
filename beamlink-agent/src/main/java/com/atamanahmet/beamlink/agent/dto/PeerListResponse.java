package com.atamanahmet.beamlink.agent.dto;

import com.atamanahmet.beamlink.agent.domain.Peer;
import lombok.Data;

import java.util.List;

@Data
public class PeerListResponse {
    private List<Peer> peers;
    private long version;
}
