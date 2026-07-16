package com.atlas.notification_service.controller;

import com.atlas.notification_service.dto.NotifyRequest;
import com.atlas.notification_service.dto.WebSocketStatusUpdate;
import com.atlas.notification_service.service.NotificationService;
import com.atlas.notification_service.websocket.WebSocketSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotificationService notificationService;
    private final WebSocketSessionRegistry registry;

    @PostMapping
    public ResponseEntity<Void> sendPush(@RequestBody @Valid NotifyRequest request) {
        notificationService.sendPush(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/ws")
    public ResponseEntity<Void> sendWebSocketUpdate(@RequestBody @Valid WebSocketStatusUpdate update) {
        registry.sendUpdate(update.getJobId().toString(), update);
        return ResponseEntity.ok().build();
    }
}
