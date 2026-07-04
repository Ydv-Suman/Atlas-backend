package com.atlas.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Stateless JWT token parser. Validates signature and expiry, extracts claims.
 * No token generation — that stays in auth-service.
 */
public class JwtTokenParser {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenParser.class);
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey signingKey;

    public JwtTokenParser(String secret) {
        this.signingKey = resolveSigningKey(secret);
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject() != null
                    && claims.getExpiration() != null
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public JwtClaims extractClaims(String token) {
        Claims claims = parseClaims(token);

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        if (roles == null) {
            roles = Collections.emptyList();
        }

        Boolean emailVerified = claims.get("emailVerified", Boolean.class);
        Boolean githubAuthorized = claims.get("githubAuthorized", Boolean.class);
        String tier = claims.get("tier", String.class);

        return new JwtClaims(
                claims.getSubject(),
                roles,
                emailVerified != null && emailVerified,
                githubAuthorized != null && githubAuthorized,
                tier != null ? tier : "FREE"
        );
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static SecretKey resolveSigningKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least " + MINIMUM_KEY_BYTES + " bytes (256 bits). "
                            + "Current key is " + keyBytes.length + " bytes. "
                            + "Generate a secure key with: openssl rand -base64 64"
            );
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
