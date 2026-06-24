package com.atlas.auth_service.controller;

import com.atlas.auth_service.Constants.AuthConstants;
import com.atlas.auth_service.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.dto.RegisterRequestDto;
import com.atlas.auth_service.dto.ResendOtpRequestDto;
import com.atlas.auth_service.dto.ResponseDto;
import com.atlas.auth_service.dto.UpdateUserRequestDto;
import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.dto.VerifyEmailRequestDto;
import com.atlas.auth_service.service.IUserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final IUserRegistrationService userRegistrationService;

    public UserController(IUserRegistrationService userRegistrationService) {
        this.userRegistrationService = userRegistrationService;
    }

    @PostMapping(value = "/register/public", version = "1.0")
    public ResponseEntity<ResponseDto> registerUser(@Valid @RequestBody RegisterRequestDto registerRequestDto) {
        userRegistrationService.registerUser(registerRequestDto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(AuthConstants.STATUS_201, AuthConstants.MESSAGE_201));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(value = "/register/admin", version = "1.0")
    public ResponseEntity<ResponseDto> registerAdminUser(@Valid @RequestBody AdminRegisterRequestDto adminRegisterRequestDto) {
        userRegistrationService.registerAdminUser(adminRegisterRequestDto);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new ResponseDto(AuthConstants.STATUS_201, AuthConstants.MESSAGE_201));
    }

    @PostMapping(value = "/verify-email", version = "1.0")
    public ResponseEntity<ResponseDto> verifyEmail(@Valid @RequestBody VerifyEmailRequestDto verifyEmailRequestDto) {
        userRegistrationService.verifyEmail(verifyEmailRequestDto.email(), verifyEmailRequestDto.otp());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ResponseDto(AuthConstants.STATUS_200, "Email verified successfully"));
    }

    @PostMapping(value = "/resend-otp", version = "1.0")
    public ResponseEntity<ResponseDto> resendOtp(@Valid @RequestBody ResendOtpRequestDto resendOtpRequestDto) {
        userRegistrationService.resendOtp(resendOtpRequestDto.email());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(new ResponseDto(AuthConstants.STATUS_200, "OTP sent successfully"));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/fetch", version = "1.0")
    public ResponseEntity<UserDto> getAuthenticatedUser(Authentication authentication) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(userRegistrationService.getUser(authentication.getName()));
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping(value = "/update", version = "1.0")
    public ResponseEntity<ResponseDto> updateUser(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequestDto updateUserRequestDto
    ) {
        boolean updated = userRegistrationService.updateUser(authentication.getName(), updateUserRequestDto);
        return ResponseEntity
                .status(updated ? HttpStatus.NO_CONTENT : HttpStatus.EXPECTATION_FAILED)
                .body(new ResponseDto(updated ? AuthConstants.STATUS_204 : AuthConstants.STATUS_417, updated ? AuthConstants.MESSAGE_204_UPDATE : AuthConstants.MESSAGE_417_UPDATE));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping(value = "/delete", version = "1.0")
    public ResponseEntity<ResponseDto> deleteUser(Authentication authentication) {
        boolean deleted = userRegistrationService.deleteUser(authentication.getName());
        return ResponseEntity
                .status(deleted ? HttpStatus.NO_CONTENT : HttpStatus.EXPECTATION_FAILED)
                .body(new ResponseDto(deleted ? AuthConstants.STATUS_204 : AuthConstants.STATUS_417, deleted ? AuthConstants.MESSAGE_204_DELETE : AuthConstants.MESSAGE_417_DELETE));
    }
}
