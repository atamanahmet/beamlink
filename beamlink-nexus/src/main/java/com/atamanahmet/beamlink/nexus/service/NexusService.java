package com.atamanahmet.beamlink.nexus.service;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.domain.Nexus;
import com.atamanahmet.beamlink.nexus.repository.NexusRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NexusService {

    private final NexusConfig nexusConfig;
    private final NexusRepository nexusRepository;
    private final PasswordEncoder passwordEncoder;
    private final AgentTokenService agentTokenService;

    /** In-memory cache, in sync with DB */
    private Nexus nexus;

    @PostConstruct
    public synchronized void init() {

        nexus = nexusRepository.findById(1).orElseGet(() -> {
            Nexus n = new Nexus();
            n.setId(1);
            n.setNexusId(UUID.randomUUID());
            n.setNexusName(nexusConfig.getName());
            n.setIpAddress(nexusConfig.getIp());
            n.setPort(nexusConfig.getNexusPort());
            n.setUsername(nexusConfig.getAdminUsername());
            n.setEncodedPassword(passwordEncoder.encode(nexusConfig.getAdminPassword()));
            log.info("No nexus record found, seeding new nexus: name={}", n.getNexusName());
            return nexusRepository.save(n);
        });

        if (nexus.getPublicToken() == null || !isRsaToken(nexus.getPublicToken())) {
            nexus.setPublicToken(
                    agentTokenService.generatePublicToken(nexus.getNexusId(), nexus.getNexusId())
            );
            persist();
            log.info("Nexus peer public token generated.");
        }

        log.info("Nexus loaded: name={}, nexusId={}", nexus.getNexusName(), nexus.getNexusId());
    }

    private synchronized void persist() {
        nexus = nexusRepository.save(nexus);
    }

    /**
     * Peek at token type without verifying
     */
    private boolean isRsaToken(String token) {
        try {
            DecodedJWT decoded = com.auth0.jwt.JWT.decode(token);
            String type = decoded.getClaim("type").asString();
            return "AGENT_PUBLIC".equals(type);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * RSA-signed token nexus uses to identify itself to other nodes as a peer
     */
    public String getPeerPublicToken() {
        return nexus.getPublicToken();
    }

    /**
     * Updates credentials at runtime without restart.
     * Caller must have already validated currentPassword before invoking.
     */
    public synchronized void updateCredentials(String newUsername, String newEncodedPassword) {
        nexus.setUsername(newUsername);
        nexus.setEncodedPassword(newEncodedPassword);
        persist();
        log.info("Nexus credentials updated. New username: {}", newUsername);
    }

    public synchronized void updateName(String newName) {
        nexus.setNexusName(newName);
        persist();
        log.info("Nexus renamed to: {}", newName);
    }

    public synchronized Nexus getNexus() {
        return nexus;
    }

    public UUID getNexusId() {
        return nexus.getNexusId();
    }

    public String getNexusIp() {return nexus.getIpAddress();}

    public Integer getNexusPort() {return nexus.getPort();}

    public String getNexusName() {
        return nexus.getNexusName();
    }

    public String getUsername() {
        return nexus.getUsername();
    }

    public String getEncodedPassword() {
        return nexus.getEncodedPassword();
    }

    public String generateNexusToken() {
        return agentTokenService.generateNexusToken(nexus.getNexusId());
    }

    public String getPublicToken() {
        return nexus.getPublicToken();
    }

    public synchronized void storePublicToken(String token) {
        nexus.setPublicToken(token);
        persist();
    }
}