package com.atlas.auth_service.registration.controller;

import com.atlas.auth_service.auth.dto.UserDto;
import com.atlas.auth_service.registration.dto.RegisterRequestDto;
import com.atlas.auth_service.registration.service.IUserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class RegistrationController {

    private final IUserRegistrationService userRegistrationService;

    public RegistrationController(IUserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping("/register/public")
    public ResponseEntity<UserDto> registerUser(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(userRegistrationService.registerUser(registerRequestDto));
    }
}
