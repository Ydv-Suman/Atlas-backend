package com.atlas.auth_service.controller;

import com.atlas.auth_service.Constants.AuthConstants;
import com.atlas.auth_service.dto.AuthorizeResponseDto;
import com.atlas.auth_service.service.IAuthService;
import com.atlas.auth_service.service.IGithubOAuthService;
import com.atlas.shared.dto.ApiResponse;
import com.atlas.shared.security.JwtClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GithubController {

    private final IGithubOAuthService githubOAuthService;
    private final IAuthService authService;

    /**
     * Mobile app calls this (with JWT).
     * Returns GitHub authorization URL for the user to open in browser.
     */
    @PostMapping(value = "/authorize", version = "1.0")
    public ResponseEntity<AuthorizeResponseDto> authorize(Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        String username = claims.username();

        return ResponseEntity.ok(githubOAuthService.buildAuthorizationUrl(username));
    }

    /**
     * Internal endpoint for service-to-service calls.
     * Returns decrypted GitHub access token for the given username.
     */
    @GetMapping("/internal/token/{username}")
    public ResponseEntity<String> getGithubToken(@PathVariable String username) {
        return ResponseEntity.ok(githubOAuthService.getDecryptedToken(username));
    }

    /**
     * GitHub redirects here after user approves.
     * Exchanges code for token, stores encrypted token, marks user as github_authorized.
     */
    @GetMapping(value = "/callback")
    public ResponseEntity<ApiResponse<Void>> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state
    ) {
        githubOAuthService.handleCallback(code, state);

        String username = githubOAuthService.getUsernameFromState(state);
        authService.setGithubAuthorized(username);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(AuthConstants.STATUS_200, "GitHub connected successfully. You can close this window."));
    }
}
