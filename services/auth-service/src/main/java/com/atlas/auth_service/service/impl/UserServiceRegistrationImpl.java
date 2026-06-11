package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.entity.UserRole;
import com.atlas.auth_service.entity.UserTier;
import com.atlas.auth_service.exception.PasswordMismatchException;
import com.atlas.auth_service.exception.UserAlreadyExistsException;
import com.atlas.auth_service.exception.UserNotFoundException;
import com.atlas.auth_service.mapper.UserMapper;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.dto.RegisterRequestDto;
import com.atlas.auth_service.dto.UpdateUserRequestDto;
import com.atlas.auth_service.service.IUserRegistrationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserServiceRegistrationImpl implements IUserRegistrationService {

    private final AtlasUserRespsitory atlasUserRespsitory;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceRegistrationImpl(AtlasUserRespsitory atlasUserRespsitory, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public void registerUser(RegisterRequestDto registerRequestDto) {
        validatePasswordConfirmation(registerRequestDto.password(), registerRequestDto.confirmPassword());
        validateUniqueUser(registerRequestDto.username(), registerRequestDto.email());
        createUser(
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
    public void registerAdminUser(AdminRegisterRequestDto adminRegisterRequestDto) {
        validatePasswordConfirmation(adminRegisterRequestDto.password(), adminRegisterRequestDto.confirmPassword());
        validateUniqueUser(adminRegisterRequestDto.username(), adminRegisterRequestDto.email());
        createUser(
                adminRegisterRequestDto.firstName(),
                adminRegisterRequestDto.middleName(),
                adminRegisterRequestDto.lastName(),
                adminRegisterRequestDto.username(),
                adminRegisterRequestDto.email(),
                adminRegisterRequestDto.password(),
                UserRole.ROLE_ADMIN
        );
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        if (!password.equals(confirmPassword)) {
            throw new PasswordMismatchException("Password and confirm password do not match");
        }
    }

    private void validateUniqueUser(String username, String email) {
        if (atlasUserRespsitory.existsByUsername(username)) {
            throw new UserAlreadyExistsException("Username is already taken");
        }

        if (atlasUserRespsitory.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }
    }

    private void createUser(
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
        atlasUserRespsitory.save(atlasUsers);
    }


    @Override
    public UserDto getUser(String username) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        return userMapper.toUserDto(atlasUsers);
    }

    @Override
    @Transactional
    public boolean updateUser(String username, UpdateUserRequestDto updateUserRequestDto) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));

        validateUpdatedUser(atlasUsers, updateUserRequestDto);
        applyUserUpdates(atlasUsers, updateUserRequestDto);
        atlasUserRespsitory.save(atlasUsers);
        return true;
    }

    private void validateUpdatedUser(AtlasUsers atlasUsers, UpdateUserRequestDto updateUserRequestDto) {
        String updatedUsername = updateUserRequestDto.username();
        if (updatedUsername != null
                && !updatedUsername.equals(atlasUsers.getUsername())
                && atlasUserRespsitory.existsByUsername(updatedUsername)) {
            throw new UserAlreadyExistsException("Username is already taken");
        }

        String updatedEmail = updateUserRequestDto.email();
        if (updatedEmail != null
                && !updatedEmail.equals(atlasUsers.getEmail())
                && atlasUserRespsitory.existsByEmail(updatedEmail)) {
            throw new UserAlreadyExistsException("Email is already registered");
        }
    }

    private void applyUserUpdates(AtlasUsers atlasUsers, UpdateUserRequestDto updateUserRequestDto) {
        if (updateUserRequestDto.firstName() != null) {
            atlasUsers.setFirstName(updateUserRequestDto.firstName());
        }
        if (updateUserRequestDto.middleName() != null) {
            atlasUsers.setMiddleName(updateUserRequestDto.middleName());
        }
        if (updateUserRequestDto.lastName() != null) {
            atlasUsers.setLastName(updateUserRequestDto.lastName());
        }
        if (updateUserRequestDto.username() != null) {
            atlasUsers.setUsername(updateUserRequestDto.username());
        }
        if (updateUserRequestDto.email() != null) {
            atlasUsers.setEmail(updateUserRequestDto.email());
        }
    }

    @Override
    @Transactional
    public boolean deleteUser(String username) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found with username: " + username));
        atlasUserRespsitory.delete(atlasUsers);
        return true;
    }
}
