package com.atlas.auth_service.dto;

import com.atlas.auth_service.entity.UserRole;
import com.atlas.auth_service.entity.UserTier;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class UserDto {

    private String firstName;
    private String middleName;
    private String lastName;
    private String username;
    private String email;
    private UserRole role;
    private UserTier tier;
    private boolean emailVerified;
    private boolean githubAuthorized;
    private Instant createdAt;
}
