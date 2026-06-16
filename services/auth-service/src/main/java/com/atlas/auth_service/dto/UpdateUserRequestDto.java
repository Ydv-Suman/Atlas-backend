package com.atlas.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequestDto(
        @Size(max = 50)
        String firstName,

        @Size(max = 50)
        String middleName,

        @Size(max = 50)
        String lastName,

        @Size(min = 5, max = 50)
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may only contain letters, digits, dots, hyphens, and underscores")
        String username,

        @Size(max = 100)
        @Email
        String email,

        String currentPassword,

        @Size(min = 8, max = 200, message = "Password must be between 8 and 200 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character"
        )
        String password
) {
}
