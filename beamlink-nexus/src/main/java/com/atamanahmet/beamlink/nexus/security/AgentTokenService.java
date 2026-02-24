package com.atamanahmet.beamlink.nexus.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class AgentTokenService {

    @Value("${nexus.jwt.secret}")
    private String secret;

    public String generateAuthToken(UUID agentId, String agentName) {
        return JWT.create()
                .withSubject(agentId.toString())
                .withClaim("name", agentName)
                .withClaim("scope", "auth")
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC512(secret));
    }

    public String generatePublicToken(UUID agentId, String agentName) {
        return JWT.create()
                .withSubject(agentId.toString())
                .withClaim("name", agentName)
                .withClaim("scope", "public")
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC512(secret));
    }

    public boolean validateToken(String token) {
        try {
            JWT.require(Algorithm.HMAC512(secret))
                    .build()
                    .verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    public UUID extractAgentId(String token) {
        try {
            DecodedJWT decoded = JWT.require(Algorithm.HMAC512(secret))
                    .build()
                    .verify(token);
            return UUID.fromString(decoded.getSubject());
        } catch (JWTVerificationException e) {
            throw new JWTVerificationException("Invalid or expired token");
        }
    }

    public String extractScope(String token) {
        return JWT.require(Algorithm.HMAC512(secret))
                .build()
                .verify(token)
                .getClaim("scope")
                .asString();
    }

    public String generateAdminToken(String username) {
        return JWT.create()
                .withSubject(username)
                .withClaim("scope", "admin")
                .withIssuedAt(new Date())
                .sign(Algorithm.HMAC512(secret));
    }

    public String extractSubject(String token) {
        return JWT.require(Algorithm.HMAC512(secret))
                .build()
                .verify(token)
                .getSubject();
    }


}