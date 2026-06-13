package com.atlas.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequestDto(
        @Size(max = 100, message = "Email must not exceed 100 characters")
        @Email(message = "Email must be a valid email address")
        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "OTP is required")
        @Size(min = 6, max = 6, message = "OTP must be 6 digits")
        String otp
) {
}
