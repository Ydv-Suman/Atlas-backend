package com.atlas.github_service.controller;

import com.atlas.github_service.constants.GithubConstants;
import com.atlas.github_service.dto.AuthorizeResponseDto;
import com.atlas.shared.dto.ApiResponse;
import com.atlas.shared.security.JwtClaims;
import com.atlas.github_service.service.IGithubService;
import com.atlas.github_service.service.client.AuthFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GithubController {

    private final IGithubService githubService;
    private final AuthFeignClient authFeignClient;

    /**
     * Mobile app calls this (with JWT).
     * Returns GitHub authorization URL for the user to open in browser.
     */
    @PostMapping(value = "/authorize", version = "1.0")
    public ResponseEntity<AuthorizeResponseDto> authorize(Authentication authentication) {
        JwtClaims claims = (JwtClaims) authentication.getDetails();
        String email = claims.email();

        return ResponseEntity.ok(githubService.buildAuthorizationUrl(email));
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
        githubService.handleCallback(code, state);

        // Extract email from state to notify auth-service
        String email = githubService.getEmailFromState(state);
        authFeignClient.setGithubAuthorized(email);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success(GithubConstants.STATUS_200, GithubConstants.MESSAGE_200_CALLBACK));
    }
}
