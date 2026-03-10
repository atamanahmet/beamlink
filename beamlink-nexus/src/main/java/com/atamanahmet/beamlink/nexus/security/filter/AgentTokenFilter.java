package com.atamanahmet.beamlink.nexus.security.filter;

import com.atamanahmet.beamlink.nexus.domain.Agent;
import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
import com.atamanahmet.beamlink.nexus.security.enums.Role;
import com.atamanahmet.beamlink.nexus.security.enums.TokenType;
import com.atamanahmet.beamlink.nexus.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTokenFilter extends OncePerRequestFilter {

    private final AgentTokenService agentTokenService;
    private final AgentRepository agentRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null) {
                processToken(token);
            }
        } catch (InvalidTokenException e) {
            log.warn("Token processing failed: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }

    private void processToken(String token) {
        TokenType tokenType = agentTokenService.extractTokenType(token);

        switch (tokenType) {
            case ADMIN -> setAdminAuthentication(token);
            case AGENT_AUTH -> setAgentAuthAuthentication(token);
            case AGENT_PUBLIC -> setAgentPublicAuthentication(token);
        }
    }

    /**
     * Extracts JWT from cookie or header
     */
    private String extractToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            String cookieToken = Arrays.stream(request.getCookies())
                    .filter(c -> "nexus_token".equals(c.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            if (cookieToken != null) return cookieToken;
        }

        return request.getHeader("X-Auth-Token");
    }

    /**
     * Sets admin authentication in SecurityContext
     */
    private void setAdminAuthentication(String token) {
        String subject = agentTokenService.extractSubject(token);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subject,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.ADMIN.getAuthority()))
                )
        );
    }

    /**
     * Sets agent authentication after verifying token and agent state
     */
    private void setAgentAuthAuthentication(String token) {
        UUID agentId = agentTokenService.extractAgentId(token);

        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            log.warn("Auth token references unknown agent: {}", agentId);
            return;
        }

        if (agent.getState() != AgentState.APPROVED) {
            log.warn("Auth token rejected for non-approved agent: {}", agentId);
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        agentId,
                        null,
                        List.of(new SimpleGrantedAuthority(Role.AGENT.getAuthority()))
                )
        );
    }

    private void setAgentPublicAuthentication(String token) {
        UUID publicId = agentTokenService.extractPublicId(token);

        Agent agent = agentRepository.findByPublicId(publicId).orElse(null);
        if (agent == null) {
            log.warn("Public token references unknown publicId: {}", publicId);
            return;
        }

        if (agent.getState() != AgentState.APPROVED) {
            log.warn("Public token rejected for non-approved agent: {}", agent.getId());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        agent.getId(),
                        null,
                        List.of(new SimpleGrantedAuthority(Role.AGENT_PUBLIC.getAuthority()))
                )
        );
    }
}