package com.atlas.auth_service.service;

import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.dto.AdminRegisterRequestDto;
import com.atlas.auth_service.dto.RegisterRequestDto;
import com.atlas.auth_service.dto.UpdateUserRequestDto;

public interface IUserRegistrationService {

    void registerUser(RegisterRequestDto registerRequestDto);

    void registerAdminUser(AdminRegisterRequestDto adminRegisterRequestDto);

    UserDto getUser(String username);

    boolean updateUser(String username, UpdateUserRequestDto updateUserRequestDto);

    boolean deleteUser(String username);
}
