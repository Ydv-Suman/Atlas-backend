package com.atlas.auth_service.service.impl;

import com.atlas.auth_service.dto.LoginRequestDto;
import com.atlas.auth_service.dto.LoginResponseDto;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.exception.InvalidCredentialsException;
import com.atlas.auth_service.mapper.UserMapper;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.security.JwtService;
import com.atlas.auth_service.service.IAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String DUMMY_HASH =
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012345";

    private final AtlasUserRespsitory atlasUserRespsitory;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public AuthServiceImpl(
            AtlasUserRespsitory atlasUserRespsitory,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            JwtService jwtService
    ) {
        this.atlasUserRespsitory = atlasUserRespsitory;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    @Override
    public LoginResponseDto login(LoginRequestDto loginRequestDto) {
        AtlasUsers atlasUsers = atlasUserRespsitory.findByUsername(loginRequestDto.username())
                .orElse(null);

        String storedHash = (atlasUsers != null) ? atlasUsers.getHashedPassword() : DUMMY_HASH;
        boolean passwordMatches = passwordEncoder.matches(loginRequestDto.password(), storedHash);

        if (atlasUsers == null || !passwordMatches) {
            log.warn("Failed login attempt for username: {}", loginRequestDto.username());
            throw new InvalidCredentialsException("Invalid username or password");
        }

        log.info("Successful login for username: {}", loginRequestDto.username());
        return new LoginResponseDto(
                "Login successful",
                userMapper.toUserDto(atlasUsers),
                jwtService.generateToken(atlasUsers)
        );
    }
}
