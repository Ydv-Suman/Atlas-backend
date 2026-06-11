package com.atlas.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendOtpRequestDto(
        @Size(max = 254, message = "Email must not exceed 254 characters")
        @Email(message = "Email must be a valid email address")
        @NotBlank(message = "Email is required")
        String email
) {
}
