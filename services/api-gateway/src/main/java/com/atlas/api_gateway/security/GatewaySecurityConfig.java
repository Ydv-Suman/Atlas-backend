package com.atlas.api_gateway.security;

import com.atlas.shared.security.JwtTokenParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    private final JwtTokenParser jwtTokenParser;

    public GatewaySecurityConfig(JwtTokenParser jwtTokenParser) {
        this.jwtTokenParser = jwtTokenParser;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
                        .contentTypeOptions(contentType -> {})
                        .hsts(hsts -> hsts
                                .includeSubdomains(true)
                                .maxAge(java.time.Duration.ofDays(365))
                        )
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                        .permissionsPolicy(permissions -> permissions
                                .policy("geolocation=(), microphone=(), camera=()"))
                        .referrerPolicy(referrer -> {})
                        .cache(cache -> {})
                )
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/auth/internal/**", "/api/users/internal/**", "/api/workspace/internal/**", "/api/notify/internal/**", "/api/agent/internal/**").denyAll()
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/users/register/public",
                                "/api/users/verify-email",
                                "/api/users/resend-otp",
                                "/api/csrf/public",
                                "/api/github/callback",
                                "/actuator/health",
                                "/actuator/info",
                                "/api/agent/webhook/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(new JwtAuthenticationWebFilter(jwtTokenParser), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
