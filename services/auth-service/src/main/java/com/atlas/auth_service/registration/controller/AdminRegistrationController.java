package com.atlas.auth_service.registration.controller;

import com.atlas.auth_service.auth.dto.UserDto;
import com.atlas.auth_service.registration.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.registration.service.IUserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminRegistrationController {

    private final IUserRegistrationService userRegistrationService;

    public AdminRegistrationController(IUserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/users")
    public ResponseEntity<UserDto> registerAdminUser(@Valid @RequestBody AdminRegisterRequestDto adminRegisterRequestDto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userRegistrationService.registerAdminUser(adminRegisterRequestDto));
    }
}
