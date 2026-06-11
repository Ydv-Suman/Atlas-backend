package com.atlas.auth_service.dto;

import com.atlas.auth_service.entity.UserTier;
import com.atlas.auth_service.entity.UserRole;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class UserDto {

    private String username;
    private String email;
    private UserRole role;
    private String firstName;
    private String middleName;
    private String lastName;
    private UserTier tier;
    private Instant createdAt;
    private Instant updatedAt;
}
