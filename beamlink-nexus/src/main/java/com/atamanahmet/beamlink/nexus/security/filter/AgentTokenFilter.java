package com.atamanahmet.beamlink.nexus.security.filter;

import com.atamanahmet.beamlink.nexus.domain.enums.AgentState;
import com.atamanahmet.beamlink.nexus.repository.AgentRepository;
import com.atamanahmet.beamlink.nexus.security.AgentTokenService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentTokenFilter extends OncePerRequestFilter {

    private final AgentTokenService agentTokenService;
    private final AgentRepository agentRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && agentTokenService.validateToken(token)) {

            String scope = agentTokenService.extractScope(token);

            if ("admin".equals(scope)) {
                setAdminAuthentication(token);
            } else if ("auth".equals(scope) || "public".equals(scope)) {
                setAgentAuthentication(token);
            }
        }

        // Always continue the chain
        chain.doFilter(request, response);
    }

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

    private void setAdminAuthentication(String token) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        agentTokenService.extractSubject(token),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                );

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void setAgentAuthentication(String token) {
        UUID agentId = agentTokenService.extractAgentId(token);

        agentRepository.findById(agentId).ifPresent(agent -> {
            if (agent.getState() == AgentState.APPROVED) {

                boolean isAuthToken = token.equals(agent.getAuthToken());
                boolean isPublicToken = token.equals(agent.getPublicToken());

                if (isAuthToken || isPublicToken) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    agentId,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_AGENT"))
                            );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        });
    }
}