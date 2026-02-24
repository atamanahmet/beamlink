package com.atamanahmet.beamlink.nexus.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DynamicCorsRegistry {

    // Always-allowed origins (admin dashboard, dev)
    private final Set<String> allowedOrigins = ConcurrentHashMap.newKeySet();

    public DynamicCorsRegistry(@Value("${nexus.cors.static-origins:}") String staticOrigins) {
        // Seed from config if provided, not required, devonly later ui will be static
        if (staticOrigins != null && !staticOrigins.isBlank()) {
            Arrays.stream(staticOrigins.split(","))
                    .map(String::trim)
                    .forEach(allowedOrigins::add);
        }
    }

    public void register(String origin) {

        allowedOrigins.add(origin);
    }

    public void unregister(String origin) {

        allowedOrigins.remove(origin);
    }

    public Set<String> getAllowedOrigins() {

        return Collections.unmodifiableSet(allowedOrigins);
    }

    public boolean isAllowed(String origin) {

        return allowedOrigins.contains(origin);
    }

    public void unregisterAll(List<String> origins){

        origins.forEach(this::unregister);
    }
}