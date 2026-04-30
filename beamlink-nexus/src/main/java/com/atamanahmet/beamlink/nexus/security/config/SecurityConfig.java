package com.atamanahmet.beamlink.nexus.security.config;

import com.atamanahmet.beamlink.nexus.security.DynamicCorsFilter;
import com.atamanahmet.beamlink.nexus.security.filter.AgentTokenFilter;
import com.atamanahmet.beamlink.nexus.security.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AgentTokenFilter agentTokenFilter;
    private final DynamicCorsFilter dynamicCorsFilter;

    @Value("${nexus.admin.username}")
    private String adminUsername;

    @Value("${nexus.admin.password}")
    private String adminPassword;

    private static final String ADMIN = Role.ADMIN.name();
    private static final String AGENT = Role.AGENT.name();
    private static final String AGENT_PUBLIC = Role.AGENT_PUBLIC.name();

    private static final String[] AGENT_TO_AGENT = {
            "/api/nexus/transfers/receive",
            "/api/nexus/transfers/*/chunk",
            "/api/nexus/transfers/*/offset"
    };

    private static final String[] UNAUTHED_AGENT = {
            "/api/agents/register",
            "/api/agents/status",
            "/api/agents/ping",
            "/api/agents/identify",
            "/api/agents/check-approval",
            "/api/agents/*/exists",
            "/ws/agents"
    };

    private static final String[] PUBLIC_ASSETS = {
            "/",
            "/index.html",
            "/assets/**",
            "/*.js",
            "/*.css",
            "/*.svg",
            "/vite.svg",
            "/h2-console/**"
    };

    private static final String[] UPLOAD = {
            "/api/upload/check",
            "/api/upload"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .cors(AbstractHttpConfigurer::disable) // handled by DynamicCorsFilter
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(dynamicCorsFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(agentTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ASSETS).permitAll()
                        .requestMatchers(UNAUTHED_AGENT).permitAll()
                        .requestMatchers(AGENT_TO_AGENT).permitAll()
                        .requestMatchers("/api/nexus/auth/login").permitAll()
                        .requestMatchers(UPLOAD).hasAnyRole(AGENT, AGENT_PUBLIC)
                        .requestMatchers("/api/nexus/peers/**").hasAnyRole(AGENT, AGENT_PUBLIC, ADMIN)
                        .requestMatchers("/api/nexus/auth/identity").hasAnyRole(AGENT, ADMIN)
                        .requestMatchers("/api/nexus/**").hasRole(ADMIN)
                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager(
                User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder().encode(adminPassword))
                        .roles(ADMIN)
                        .build()
        );
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}