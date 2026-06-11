package com.atlas.auth_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequestDto(
        @Size(max = 50)
        String firstName,

        @Size(max = 50)
        String middleName,

        @Size(max = 50)
        String lastName,

        @Size(min = 5, max = 50)
        String username,

        @Size(max = 254)
        @Email
        String email
) {
}
