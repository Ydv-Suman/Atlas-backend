package com.atlas.auth_service.mapper;

import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.entity.AtlasUsers;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toUserDto(AtlasUsers atlasUsers) {
        UserDto userDto = new UserDto();
        userDto.setFirstName(atlasUsers.getFirstName());
        userDto.setMiddleName(atlasUsers.getMiddleName());
        userDto.setLastName(atlasUsers.getLastName());
        userDto.setUsername(atlasUsers.getUsername());
        userDto.setEmail(atlasUsers.getEmail());
        userDto.setRole(atlasUsers.getRole());
        userDto.setTier(atlasUsers.getTier());
        userDto.setEmailVerified(atlasUsers.isEmailVerified());
        userDto.setGithubAuthorized(atlasUsers.isGithubAuthorized());
        userDto.setCreatedAt(atlasUsers.getCreatedAt());
        return userDto;
    }
}
