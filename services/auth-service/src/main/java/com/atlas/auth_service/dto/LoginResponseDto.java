package com.atlas.auth_service.dto;

public record LoginResponseDto(
        String message,
        UserDto user,
        String jwtToken
) {
}
