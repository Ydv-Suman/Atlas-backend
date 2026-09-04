package com.atlas.shared.security;

import java.util.List;

/**
 * Immutable container for claims extracted from a validated JWT.
 * Every service gets the same view of the authenticated user.
 */
public record JwtClaims(
        String userId,
        String username,
        List<String> roles,
        boolean emailVerified,
        boolean githubAuthorized,
        String tier
) {
}
