package com.atlas.api_gateway.security;

import com.atlas.shared.security.JwtTokenParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/users/register/public",
                                "/api/users/verify-email",
                                "/api/users/resend-otp",
                                "/api/github/callback",
                                "/api/csrf/public",
                                "/actuator/**"
                        ).permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(new JwtAuthenticationWebFilter(jwtTokenParser), SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
