package com.atlas.auth_service.service;

import com.atlas.auth_service.dto.LoginRequestDto;
import com.atlas.auth_service.dto.LoginResponseDto;

public interface IAuthService {

    LoginResponseDto login(LoginRequestDto loginRequestDto);

    void logout(String token);

    boolean isTokenBlacklisted(String token);
}
