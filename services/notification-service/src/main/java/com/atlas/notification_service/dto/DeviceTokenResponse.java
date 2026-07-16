package com.atlas.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DeviceTokenResponse {

    @JsonProperty("fcmToken")
    private String fcmToken;

    @JsonProperty("deviceOs")
    private String deviceOs;
}
