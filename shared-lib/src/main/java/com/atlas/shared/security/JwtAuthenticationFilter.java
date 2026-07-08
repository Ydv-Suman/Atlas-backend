package com.atlas.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Generic JWT filter every service can reuse.
 * Parses Bearer token, validates, sets SecurityContext with JwtClaims as principal.
 * <p>
 * Services needing extra validation (e.g. token blacklist) override {@link #isTokenAllowed(String)}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenParser tokenParser;

    public JwtAuthenticationFilter(JwtTokenParser tokenParser) {
        this.tokenParser = tokenParser;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (tokenParser.isValid(token)
                    && isTokenAllowed(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                JwtClaims claims = tokenParser.extractClaims(token);

                List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList();

                // Principal = username (getName() works), details = full JwtClaims
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(claims.username(), null, authorities);
                authentication.setDetails(claims);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Override in services that need extra token checks (e.g. blacklist).
     * Default: all valid tokens allowed.
     */
    protected boolean isTokenAllowed(String token) {
        return true;
    }
}
