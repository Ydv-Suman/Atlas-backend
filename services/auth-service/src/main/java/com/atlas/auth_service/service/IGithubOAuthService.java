package com.atlas.auth_service.service;

import com.atlas.auth_service.dto.AuthorizeResponseDto;

public interface IGithubOAuthService {

    AuthorizeResponseDto buildAuthorizationUrl(String username);

    String handleCallback(String code, String state);

    String getUsernameFromState(String state);
}
