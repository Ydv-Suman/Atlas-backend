package com.atlas.notification_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.UUID;

@Getter
@Setter
@ToString
public class NotifyRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private UUID jobId;

    @NotBlank
    private String status;

    @NotBlank
    private String message;
}
