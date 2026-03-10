package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.config.JwtConfig;
import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.dto.LoginRequest;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.crypto.password.PasswordEncoder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/nexus/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AgentTokenService agentTokenService;
    private final PasswordEncoder passwordEncoder;
    private final NexusConfig nexusConfig;
    private final JwtConfig jwtConfig;

    private static final UUID NEXUS_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");



    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("Login attempt for user: {}", request.username());

        if (!request.username().equals(nexusConfig.getAdminUsername()) ||
                !passwordEncoder.matches(request.password(), passwordEncoder.encode(nexusConfig.getAdminPassword()))) {
            log.warn("Failed login attempt for user: {}", request.username());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = agentTokenService.generateAdminToken(request.username());

        Cookie cookie = new Cookie("nexus_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtConfig.getAdminExpirationMinutes() * 60));

        response.addCookie(cookie);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("nexus_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }

    /**
     * Returns Nexus public identity for agent's peerlist
     */
    @GetMapping("/identity")
    public ResponseEntity<Map<String, Object>> getNexusIdentity() {

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Map.of("publicId", NEXUS_PUBLIC_ID));
    }
}