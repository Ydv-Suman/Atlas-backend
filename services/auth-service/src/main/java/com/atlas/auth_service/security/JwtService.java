package com.atlas.auth_service.security;

import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.shared.security.JwtTokenParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Auth-service only — generates JWT tokens with full claims.
 * Parsing/validation delegated to shared-lib JwtTokenParser.
 */
@Component
public class JwtService {

    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMs;
    private final JwtTokenParser tokenParser;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            JwtTokenParser tokenParser
    ) {
        this.signingKey = resolveSigningKey(secret);
        this.expirationMs = expirationMs;
        this.tokenParser = tokenParser;
    }

    public String generateToken(AtlasUsers user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .issuer("atlas-auth-service")
                .audience().add("atlas-api").and()
                .subject(user.getUsername())
                .claim("userId", user.getId().toString())
                .claim("roles", List.of(user.getRole().name()))
                .claim("emailVerified", user.isEmailVerified())
                .claim("githubAuthorized", user.isGithubAuthorized())
                .claim("tier", user.getTier().name())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return tokenParser.extractSubject(token);
    }

    public boolean isTokenValid(String token) {
        return tokenParser.isValid(token);
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
