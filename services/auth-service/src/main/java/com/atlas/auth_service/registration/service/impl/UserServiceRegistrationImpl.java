package com.atlas.auth_service.registration.service.impl;

import com.atlas.auth_service.auth.dto.UserDto;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.entity.UserRole;
import com.atlas.auth_service.entity.UserTier;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.registration.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.registration.dto.RegisterRequestDto;
import com.atlas.auth_service.registration.service.IUserRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserServiceRegistrationImpl implements IUserRegistrationService {

    private final AtlasUserRespsitory atlasUserRespsitory;
    private final PasswordEncoder passwordEncoder;

    public UserServiceRegistrationImpl(AtlasUserRespsitory atlasUserRespsitory, PasswordEncoder passwordEncoder) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public UserDto registerUser(RegisterRequestDto registerRequestDto) {
        validatePasswordConfirmation(registerRequestDto);
        validateUniqueUser(registerRequestDto);
        return createUser(
                registerRequestDto.firstName(),
                registerRequestDto.middleName(),
                registerRequestDto.lastName(),
                registerRequestDto.username(),
                registerRequestDto.email(),
                registerRequestDto.password(),
                UserRole.ROLE_USER
        );
    }

    @Override
    @Transactional
    public UserDto registerAdminUser(AdminRegisterRequestDto adminRegisterRequestDto) {
        validatePasswordConfirmation(adminRegisterRequestDto.password(), adminRegisterRequestDto.confirmPassword());
        validateUniqueUser(adminRegisterRequestDto.username(), adminRegisterRequestDto.email());
        return createUser(
                adminRegisterRequestDto.firstName(),
                adminRegisterRequestDto.middleName(),
                adminRegisterRequestDto.lastName(),
                adminRegisterRequestDto.username(),
                adminRegisterRequestDto.email(),
                adminRegisterRequestDto.password(),
                UserRole.ROLE_ADMIN
        );
    }

    private void validatePasswordConfirmation(RegisterRequestDto registerRequestDto) {
        validatePasswordConfirmation(registerRequestDto.password(), registerRequestDto.confirmPassword());
    }

    private void validateUniqueUser(RegisterRequestDto registerRequestDto) {
        validateUniqueUser(registerRequestDto.username(), registerRequestDto.email());
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match");
        }
    }

    private void validateUniqueUser(String username, String email) {
        if (atlasUserRespsitory.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already taken");
        }

        if (atlasUserRespsitory.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
    }

    private UserDto createUser(
            String firstName,
            String middleName,
            String lastName,
            String username,
            String email,
            String password,
            UserRole role
    ) {
        AtlasUsers atlasUsers = new AtlasUsers();
        atlasUsers.setId(UUID.randomUUID());
        atlasUsers.setFirstName(firstName);
        atlasUsers.setMiddleName(middleName);
        atlasUsers.setLastName(lastName);
        atlasUsers.setUsername(username);
        atlasUsers.setEmail(email);
        atlasUsers.setRole(role);
        atlasUsers.setHashedPassword(passwordEncoder.encode(password));
        atlasUsers.setEmailVerified(false);
        atlasUsers.setGithubAuthorized(false);
        atlasUsers.setTier(UserTier.FREE);

        return toUserDto(atlasUserRespsitory.save(atlasUsers));
    }

    private UserDto toUserDto(AtlasUsers atlasUsers) {
        UserDto userDto = new UserDto();
        userDto.setId(atlasUsers.getId());
        userDto.setUsername(atlasUsers.getUsername());
        userDto.setEmail(atlasUsers.getEmail());
        userDto.setRole(atlasUsers.getRole());
        userDto.setFirstName(atlasUsers.getFirstName());
        userDto.setMiddleName(atlasUsers.getMiddleName());
        userDto.setLastName(atlasUsers.getLastName());
        userDto.setTier(atlasUsers.getTier());
        userDto.setCreatedAt(atlasUsers.getCreatedAt());
        userDto.setUpdatedAt(atlasUsers.getUpdatedAt());
        return userDto;
    }
}
