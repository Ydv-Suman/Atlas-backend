package com.atlas.auth_service.service;

import com.atlas.auth_service.dto.AuthorizeResponseDto;

import java.util.Optional;

public interface IGithubOAuthService {

    AuthorizeResponseDto buildAuthorizationUrl(String username);

    String handleCallback(String code, String state);

    String getUsernameFromState(String state);

    Optional<String> getDecryptedToken(String username);

    void disconnectGithub(String username);
}
