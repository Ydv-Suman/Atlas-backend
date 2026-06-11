package com.atlas.auth_service.registration.service;

import com.atlas.auth_service.auth.dto.UserDto;
import com.atlas.auth_service.registration.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.registration.dto.RegisterRequestDto;

public interface IUserRegistrationService {

    UserDto registerUser(RegisterRequestDto registerRequestDto);

    UserDto registerAdminUser(AdminRegisterRequestDto adminRegisterRequestDto);
}
