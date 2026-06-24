package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.dto.LoginRequestDto;
import com.atlas.auth_service.dto.LoginResponseDto;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.exception.EmailNotVerifiedException;
import com.atlas.auth_service.exception.InvalidCredentialsException;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.security.JwtService;
import com.atlas.auth_service.service.IAuthService;
import com.atlas.auth_service.service.LoginRateLimitService;
import com.atlas.auth_service.service.TokenBlacklistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String DUMMY_HASH =
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345";
    private final AtlasUserRespsitory atlasUserRespsitory;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimitService loginRateLimitService;
    private final TokenBlacklistService tokenBlacklistService;
    private final long tokenExpirationMs;

    public AuthServiceImpl(
            AtlasUserRespsitory atlasUserRespsitory,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimitService loginRateLimitService,
            TokenBlacklistService tokenBlacklistService,
            @org.springframework.beans.factory.annotation.Value("${app.jwt.expiration-ms}") long tokenExpirationMs
    ) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginRateLimitService = loginRateLimitService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.tokenExpirationMs = tokenExpirationMs;
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        loginRateLimitService.checkRateLimit(loginRequestDto.username());

        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(loginRequestDto.username())
                .orElse(null);

        String storedHash = (atlasUsers != null) ? atlasUsers.getHashedPassword() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(loginRequestDto.password(), storedHash);

        if (atlasUsers == null || !passwordMatches) {
            loginRateLimitService.recordFailedAttempt(loginRequestDto.username());
            log.warn("Failed login attempt for username: {}", loginRequestDto.username());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        if (!atlasUsers.isEmailVerified()) {
            throw new EmailNotVerifiedException("Email not verified. Please verify your email before logging in");
        }

        loginRateLimitService.resetAttempts(loginRequestDto.username());
        log.info("Successful login for username: {}", loginRequestDto.username());
        return new LoginResponseDto(
                "Login successful",
                atlasUsers.getUsername(),
                atlasUsers.getEmail(),
                jwtService.generateToken(atlasUsers)
        );
    }

    @Override
    public void logout(String token) {
        tokenBlacklistService.blacklist(token, Duration.ofMillis(tokenExpirationMs));
        log.info("Token blacklisted successfully");
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        return tokenBlacklistService.isBlacklisted(token);
    }

    @Override
    public void setGithubAuthorized(String email) {
        AtlasUsers user = atlasUserRespsitory.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found with email: " + email));
        user.setGithubAuthorized(true);
        atlasUserRespsitory.save(user);
        log.info("GitHub authorized for user: {}", email);
    }
}
