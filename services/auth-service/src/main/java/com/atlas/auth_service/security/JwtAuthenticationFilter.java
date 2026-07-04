package com.atlas.auth_service.security;

import com.atlas.auth_service.service.TokenBlacklistService;
import com.atlas.shared.security.JwtTokenParser;
import org.springframework.stereotype.Component;

/**
 * Auth-service JWT filter — extends shared filter with token blacklist check.
 */
@Component
public class JwtAuthenticationFilter extends com.atlas.shared.security.JwtAuthenticationFilter {

    private final TokenBlacklistService tokenBlacklistService;

    public JwtAuthenticationFilter(JwtTokenParser tokenParser, TokenBlacklistService tokenBlacklistService) {
        super(tokenParser);
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected boolean isTokenAllowed(String token) {
        return !tokenBlacklistService.isBlacklisted(token);
    }
}
