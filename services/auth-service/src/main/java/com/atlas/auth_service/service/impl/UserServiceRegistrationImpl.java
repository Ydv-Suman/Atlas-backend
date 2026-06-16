package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.entity.UserRole;
import com.atlas.auth_service.entity.UserTier;
import com.atlas.auth_service.exception.EmailNotVerifiedException;
import com.atlas.auth_service.exception.OtpException;
import com.atlas.auth_service.exception.PasswordMismatchException;
import com.atlas.auth_service.exception.UserAlreadyExistsException;
import com.atlas.auth_service.exception.InvalidCredentialsException;
import com.atlas.auth_service.exception.UserNotFoundException;
import com.atlas.auth_service.mapper.UserMapper;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.dto.RegisterRequestDto;
import com.atlas.auth_service.dto.UpdateUserRequestDto;
import com.atlas.auth_service.service.EmailService;
import com.atlas.auth_service.service.IUserRegistrationService;
import com.atlas.auth_service.service.OtpService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserServiceRegistrationImpl implements IUserRegistrationService {

    private final AtlasUserRespsitory atlasUserRespsitory;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final OtpService otpService;
    private final EmailService emailService;

    public UserServiceRegistrationImpl(
            AtlasUserRespsitory atlasUserRespsitory,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            OtpService otpService,
            EmailService emailService
    ) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.otpService = otpService;
        this.emailService = emailService;
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

        String otp = otpService.generateAndStoreOtp(registerRequestDto.email());
        emailService.sendOtpEmailAsync(registerRequestDto.email(), otp);
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

    private void requireVerifiedEmail(AtlasUsers user) {
        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email must be verified before this action");
        }
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
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return userMapper.toUserDto(atlasUsers);
    }

    @Override
    @Transactional
    public boolean updateUser(String username, UpdateUserRequestDto updateUserRequestDto) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        requireVerifiedEmail(atlasUsers);
        validateUpdatedUser(atlasUsers, updateUserRequestDto);
        validatePasswordChange(atlasUsers, updateUserRequestDto);
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

    private void validatePasswordChange(AtlasUsers atlasUsers, UpdateUserRequestDto updateUserRequestDto) {
        if (updateUserRequestDto.password() != null) {
            if (updateUserRequestDto.currentPassword() == null
                    || !passwordEncoder.matches(updateUserRequestDto.currentPassword(), atlasUsers.getHashedPassword())) {
                throw new InvalidCredentialsException("Current password is incorrect");
            }
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
        if (updateUserRequestDto.password() != null) {
            atlasUsers.setHashedPassword(passwordEncoder.encode(updateUserRequestDto.password()));
        }
        if (updateUserRequestDto.email() != null
                && !updateUserRequestDto.email().equals(atlasUsers.getEmail())) {
            String newEmail = updateUserRequestDto.email();
            atlasUsers.setEmail(newEmail);
            atlasUsers.setEmailVerified(false);

            String otp = otpService.generateAndStoreOtp(newEmail);
            emailService.sendOtpEmail(newEmail, otp);
        }
    }

    @Override
    @Transactional
    public boolean deleteUser(String username) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        requireVerifiedEmail(atlasUsers);
        atlasUserRespsitory.delete(atlasUsers);
        return true;
    }

    @Override
    @Transactional
    public void verifyEmail(String email, String otp) {
        AtlasUsers user = atlasUserRespsitory.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new OtpException("Email is already verified");
        }

        otpService.verifyOtp(email, otp);

        user.setEmailVerified(true);
        atlasUserRespsitory.save(user);
    }

    @Override
    public void resendOtp(String email) {
        AtlasUsers user = atlasUserRespsitory.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if (user.isEmailVerified()) {
            throw new OtpException("Email is already verified");
        }

        otpService.checkResendCooldown(email);

        String otp = otpService.generateAndStoreOtp(email);
        emailService.sendOtpEmail(email, otp);
    }
}
