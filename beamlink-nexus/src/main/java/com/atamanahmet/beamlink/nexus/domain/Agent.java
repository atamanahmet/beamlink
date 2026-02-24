package com.atamanahmet.beamlink.nexus.domain;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "registered_agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String approvedName;

    private String ipAddress;

    private int port;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentState state;

    @Column(length = 512)
    private String authToken;

    @Column(length = 512)
    private String publicToken;

    private Instant registeredAt;

    @Column(nullable = true)
    private String requestedName;

    private Instant approvedAt;

    private Instant lastSeenAt;

    @Column(nullable = false)
    private boolean approvalPushed = false;

    private List<String> extraOrigins = null;

    public boolean isOnline() {
        if (lastSeenAt == null) return false;
        return lastSeenAt.isAfter(Instant.now().minus(Duration.ofMinutes(2)));
    }
}