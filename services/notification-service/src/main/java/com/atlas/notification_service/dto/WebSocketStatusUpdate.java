package com.atlas.notification_service.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@ToString
public class WebSocketStatusUpdate {

    private UUID jobId;

    private String step;

    private String status;

    private String message;

    private Instant timestamp;

}
