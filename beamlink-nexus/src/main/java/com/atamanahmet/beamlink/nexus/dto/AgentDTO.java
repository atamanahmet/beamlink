package com.atamanahmet.beamlink.nexus.dto;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AgentDTO {

    private UUID id;

    private String agentName;

    private String approvedName;

    private String ipAddress;

    private int port;

    private String publicToken;

    private Instant registeredAt;

    private Instant approvedAt;

    private Instant lastSeenAt;

    private boolean online;
}
