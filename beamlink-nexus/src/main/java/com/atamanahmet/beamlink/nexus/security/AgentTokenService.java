package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.config.JwtConfig;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class AgentTokenService {

    private static final String ISSUER = "beamlink-nexus";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PUBLIC_ID = "publicId";

    private final Algorithm algorithm;
    private final JwtConfig jwtConfig;

    public AgentTokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
        this.algorithm = Algorithm.HMAC512(jwtConfig.getSecret());
    }

    @PostConstruct
    private void validateConfig() {
        if (jwtConfig.getSecret() == null || jwtConfig.getSecret().isBlank()) {
            throw new IllegalStateException("JWT secret must be configured (NEXUS_JWT_SECRET)");
        }
    }

    public String generateAdminToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAdminExpirationMinutes(), ChronoUnit.MINUTES);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(username)
                .withClaim(CLAIM_TYPE, TokenType.ADMIN.getValue())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    public String generateAuthToken(UUID agentId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAgentAuthExpirationDays(), ChronoUnit.DAYS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(agentId.toString())
                .withAudience("agent")
                .withClaim(CLAIM_TYPE, TokenType.AGENT_AUTH.getValue())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    // publicId is stored in DB and embedded in the token as a stable peer identity reference
    public String generatePublicToken(UUID agentId, UUID publicId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtConfig.getAgentPublicExpirationDays(), ChronoUnit.DAYS);

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(agentId.toString())
                .withAudience("agent")
                .withClaim(CLAIM_TYPE, TokenType.AGENT_PUBLIC.getValue())
                .withClaim(CLAIM_PUBLIC_ID, publicId.toString())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(expiry))
                .sign(algorithm);
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
            return true;
        } catch (JWTVerificationException e) {
            log.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public UUID extractAgentId(String token) {
        DecodedJWT decoded = decode(token);
        TokenType type = extractTokenType(decoded);
        if (type == TokenType.ADMIN) {
            throw new InvalidTokenException("Admin token cannot be used as agent token", null);
        }
        return UUID.fromString(decoded.getSubject());
    }

    public UUID extractPublicId(String token) {
        String value = decode(token).getClaim(CLAIM_PUBLIC_ID).asString();
        if (value == null) {
            throw new InvalidTokenException("Token does not contain publicId claim", null);
        }
        return UUID.fromString(value);
    }

    public String extractSubject(String token) {
        return decode(token).getSubject();
    }

    public TokenType extractTokenType(String token) {
        return extractTokenType(decode(token));
    }

    private TokenType extractTokenType(DecodedJWT decoded) {
        String type = decoded.getClaim(CLAIM_TYPE).asString();
        return TokenType.fromValue(type);
    }

    private DecodedJWT decode(String token) {
        try {
            return JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .build()
                    .verify(token);
        } catch (JWTVerificationException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }
}