package com.atlas.github_service.service;

import com.atlas.github_service.dto.AuthorizeResponseDto;

public interface IGithubService {

    AuthorizeResponseDto buildAuthorizationUrl(String email);

    String handleCallback(String code, String state);

    String getEmailFromState(String state);
}
