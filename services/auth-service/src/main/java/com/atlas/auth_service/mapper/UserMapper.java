package com.atlas.auth_service.mapper;

import com.atlas.auth_service.dto.UserDto;
import com.atlas.auth_service.entity.AtlasUsers;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toUserDto(AtlasUsers atlasUsers) {
        UserDto userDto = new UserDto();
        userDto.setUsername(atlasUsers.getUsername());
        userDto.setEmail(atlasUsers.getEmail());
        userDto.setRole(atlasUsers.getRole());
        userDto.setFirstName(atlasUsers.getFirstName());
        userDto.setMiddleName(atlasUsers.getMiddleName());
        userDto.setLastName(atlasUsers.getLastName());
        userDto.setTier(atlasUsers.getTier());
        userDto.setCreatedAt(atlasUsers.getCreatedAt());
        userDto.setUpdatedAt(atlasUsers.getUpdatedAt());
        return userDto;
    }
}
