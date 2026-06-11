package com.atlas.auth_service.auth.dto;

public record LoginResponseDto(
        String message,
        UserDto user,
        String jwtToken
) {
}
