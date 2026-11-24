package com.atlas.auth_service.controller;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.service.IDeviceTokenService;
import com.atlas.auth_service.entity.AtlasUsers;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DeviceTokenControllerTest {
    @Test
    void internalIdentityResolvesTheVerifiedUuidToItsStoredUsername() {
        UUID id=UUID.randomUUID();
        var users=mock(AtlasUserRespsitory.class);
        var user=new AtlasUsers(); user.setUsername("workspace-owner"); user.setFirstName("Test"); user.setLastName("Owner"); user.setEmail("owner@example.invalid");
        when(users.findById(id)).thenReturn(Optional.of(user));
        var identity=new DeviceTokenController(mock(IDeviceTokenService.class), users).getUserIdentity(id).getBody();
        assertEquals("workspace-owner", identity.get("username"));
        assertEquals("owner@example.invalid", identity.get("email"));
    }
}
