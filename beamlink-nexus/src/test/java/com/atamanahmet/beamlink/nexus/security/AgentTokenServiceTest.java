package com.atamanahmet.beamlink.nexus.security;

import com.atamanahmet.beamlink.nexus.config.JwtConfig;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTokenServiceTest {

    private AgentTokenService agentTokenService;

//    @Test
//    @DisplayName("context loads")
//    void contextLoads() {
//    }

    @BeforeEach
    void setUp() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("test-secret-key-for-hmac512");
        jwtConfig.setAdminExpirationMinutes(60);
        jwtConfig.setAgentAuthExpirationDays(30);
        jwtConfig.setAgentPublicExpirationDays(30);

        agentTokenService = new AgentTokenService(jwtConfig);
    }

    @Test
    @DisplayName("generates a valid auth token for agent")
    void generateAuthToken_returnsValidToken() {
        UUID agentId = UUID.randomUUID();

        String token = agentTokenService.generateAuthToken(agentId);

        assertThat(token).isNotBlank();
        assertThat(agentTokenService.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("generates a valid public token for agent")
    void generatePublicToken_returnsValidToken() {
        UUID agentId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();

        String token = agentTokenService.generatePublicToken(agentId, publicId);

        assertThat(token).isNotBlank();
        assertThat(agentTokenService.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("extracts correct agent id from auth token")
    void extractAgentId_returnsCorrectId_fromAuthToken() {
        UUID agentId = UUID.randomUUID();
        String token = agentTokenService.generateAuthToken(agentId);

        UUID extracted = agentTokenService.extractAgentId(token);

        assertThat(extracted).isEqualTo(agentId);
    }

    @Test
    @DisplayName("extracts correct public id from public token")
    void extractPublicId_returnsCorrectId_fromPublicToken() {
        UUID agentId = UUID.randomUUID();
        UUID publicId = UUID.randomUUID();
        String token = agentTokenService.generatePublicToken(agentId, publicId);

        UUID extracted = agentTokenService.extractPublicId(token);

        assertThat(extracted).isEqualTo(publicId);
    }

    @Test
    @DisplayName("throws InvalidTokenException when admin token used as agent token")
    void extractAgentId_throwsException_whenAdminTokenUsed() {
        String adminToken = agentTokenService.generateAdminToken("admin");

        assertThatThrownBy(() -> agentTokenService.extractAgentId(adminToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("returns false for invalid token")
    void validateToken_returnsFalse_whenTokenIsInvalid() {
        boolean result = agentTokenService.validateToken("this.is.not.a.valid.token");

        assertThat(result).isFalse();
    }
}