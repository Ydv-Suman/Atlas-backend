package com.atlas.auth_service.controller;

import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.service.IGithubOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth/internal/github")
@RequiredArgsConstructor
public class InternalGithubController {
    private final AtlasUserRespsitory userRepository;
    private final IGithubOAuthService githubOAuthService;

    @GetMapping("/token/{userId}")
    public ResponseEntity<String> getGithubToken(@PathVariable UUID userId) {
        return userRepository.findById(userId)
                .flatMap(user -> githubOAuthService.getDecryptedToken(user.getUsername()))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
