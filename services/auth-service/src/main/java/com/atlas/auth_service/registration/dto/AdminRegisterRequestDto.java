package com.atlas.auth_service.registration.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminRegisterRequestDto(
        @Size(max = 50)
        @NotBlank
        String firstName,

        @Size(max = 50)
        String middleName,

        @Size(max = 50)
        @NotBlank
        String lastName,

        @Size(min = 5, max = 50)
        @NotBlank
        String username,

        @Size(max = 50)
        @Email
        @NotBlank
        String email,

        @Size(min = 8, max = 200)
        @NotBlank
        String password,

        @Size(min = 8, max = 200)
        @NotBlank
        String confirmPassword
) {
}
