package com.atamanahmet.beamlink.nexus.security.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
public enum TokenType {
    ADMIN("admin"),
    AGENT_AUTH("agent-auth"),
    AGENT_PUBLIC("agent-public");

    private final String value;

    private static final Map<String, TokenType> VALUE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(TokenType::getValue, t->t));

    TokenType(String value) {
        this.value = value;
    }

    public static TokenType fromValue(String value) {
        for (TokenType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown token type: " + value);
    }
}