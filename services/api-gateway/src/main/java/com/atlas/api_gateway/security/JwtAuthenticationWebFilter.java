package com.atlas.api_gateway.security;

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
        String token = null;
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            token = exchange.getRequest().getQueryParams().getFirst("token");
        }

        if (token != null) {

            return tokenParser.validateAndExtract(token)
                    .map(claims -> {
                        var authorities = claims.roles().stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                        var authentication = new UsernamePasswordAuthenticationToken(
                                claims.username(), null, authorities);
                        authentication.setDetails(claims);

                            ServerWebExchange mutatedExchange = exchange;
                        if (claims.userId() != null) {
                            mutatedExchange = exchange.mutate()
                                    .request(r -> r.header("X-User-Id", claims.userId()))
                                    .build();
                        }

                        return chain.filter(mutatedExchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                    })
                    .orElseGet(() -> chain.filter(exchange));
        }

        return chain.filter(exchange);
    }
}
