package com.atlas.api_gateway.security;

import com.atlas.shared.security.JwtClaims;
import com.atlas.shared.security.JwtTokenParser;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

public class JwtAuthenticationWebFilter implements WebFilter {

    private final JwtTokenParser tokenParser;

    public JwtAuthenticationWebFilter(JwtTokenParser tokenParser) {
        this.tokenParser = tokenParser;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (tokenParser.isValid(token)) {
                JwtClaims claims = tokenParser.extractClaims(token);

                var authorities = claims.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(
                        claims.username(), null, authorities);
                authentication.setDetails(claims);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
            }
        }

        return chain.filter(exchange);
    }
}
