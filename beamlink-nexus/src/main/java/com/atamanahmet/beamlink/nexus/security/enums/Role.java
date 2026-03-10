package com.atamanahmet.beamlink.nexus.security.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {

    ADMIN("ROLE_ADMIN"),
    AGENT("ROLE_AGENT"),
    AGENT_PUBLIC("ROLE_AGENT_PUBLIC");

    private final String authority;
}