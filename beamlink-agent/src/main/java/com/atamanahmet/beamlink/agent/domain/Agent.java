package com.atamanahmet.beamlink.agent.domain;

import com.atamanahmet.beamlink.agent.domain.enums.AgentState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "agent")
@Getter
@Setter
@NoArgsConstructor
public class Agent {

    /* Only one agent per instance */
    @Id
    @Column(nullable = false, updatable = false)
    private Long id = 1L;

    @Column(columnDefinition = "VARCHAR(36)")
    private UUID agentId;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private int port;

    @Column
    private String authToken;

    @Column
    private String publicToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentState state = AgentState.UNREGISTERED;

    public boolean isApproved() {
        return state == AgentState.APPROVED;
    }

    public String getAddress() {
        return ipAddress + ":" + port;
    }

    public Peer toPeer() {
        return new Peer(
                agentId,
                agentName,
                ipAddress,
                port,
                System.currentTimeMillis(),
                true,
                publicToken
        );
    }
}