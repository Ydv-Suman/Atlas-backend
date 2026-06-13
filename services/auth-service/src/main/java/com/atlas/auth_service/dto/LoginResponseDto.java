package com.atlas.auth_service.dto;

public record LoginResponseDto(
        String message,
        String username,
        String email,
        String jwtToken
) {
}
