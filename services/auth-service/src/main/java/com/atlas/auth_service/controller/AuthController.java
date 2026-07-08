package com.atlas.auth_service.controller;

import com.atlas.auth_service.dto.LoginRequestDto;
import com.atlas.auth_service.dto.LoginResponseDto;
import com.atlas.shared.dto.ApiResponse;
import com.atlas.auth_service.service.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final IAuthService authService;

    public AuthController(IAuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/login", version = "1.0")
    public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto loginRequestDto) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(authService.login(loginRequestDto));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/logout", version = "1.0")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("200", "Logged out successfully"));
    }

}
