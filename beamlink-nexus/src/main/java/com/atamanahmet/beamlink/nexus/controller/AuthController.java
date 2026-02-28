package com.atamanahmet.beamlink.nexus.controller;

import com.atamanahmet.beamlink.nexus.config.NexusConfig;
import com.atamanahmet.beamlink.nexus.dto.LoginRequest;
import com.atamanahmet.beamlink.nexus.dto.LoginResponse;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/nexus/auth")
@RequiredArgsConstructor
public class AuthController {

    private final Logger log = LoggerFactory.getLogger(AuthController.class);


    private final AgentTokenService agentTokenService;


    @Value("${nexus.admin.username}")
    private String adminUsername;

    @Value("${nexus.admin.password}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {

        log.info("Login attempt for user: {}", request.username());

        if (!request.username().equals(adminUsername) ||
                !request.password().equals(adminPassword)) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid credentials");
        }

        String token = agentTokenService.generateAdminToken(request.username());

        //TODO: set secure https
        Cookie cookie = new Cookie("nexus_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 8); // 8 hours

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
        cookie.setMaxAge(0); // delete it
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/identity")
    public ResponseEntity<Map<String, Object>> getNexusIdentity() {

        String token = agentTokenService.generatePublicToken(UUID.fromString("00000000-0000-0000-0000-000000000000"),"Nexus");

        return ResponseEntity.ok(Map.of(
                "publicToken", token
        ));
    }
}

