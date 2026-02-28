package com.atamanahmet.beamlink.agent.controller;

import com.atamanahmet.beamlink.agent.dto.LoginRequest;
import com.atamanahmet.beamlink.agent.service.AgentAuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AgentAuthController {

    private final AgentAuthService agentAuthService;

    @Value("${agent.ui.username}")
    private String username;

    @Value("${agent.ui.password}")
    private String password;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        if (!request.username().equals(username) || !request.password().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        String token = agentAuthService.generateToken(request.username());

        Cookie cookie = new Cookie("agent_ui_token", token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 8);
        response.addCookie(cookie);

        String publicToken = agentAuthService.getPublicToken();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Map.of("publicToken", publicToken));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String publicToken = agentAuthService.getPublicToken();
        if (publicToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(Map.of("publicToken", publicToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response) {

        Cookie cookie = new Cookie("agent_ui_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity
                .status(HttpStatus.OK)
                .build();
    }
}