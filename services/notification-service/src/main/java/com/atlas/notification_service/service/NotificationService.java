package com.atlas.notification_service.service;

import com.atlas.notification_service.dto.DeviceTokenResponse;
import com.atlas.notification_service.dto.NotifyRequest;
import com.atlas.notification_service.feign.AuthFeignClient;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final FirebaseMessaging firebaseMessaging;
    private final AuthFeignClient authFeignClient;

    public void sendPush(NotifyRequest request) {
        List<DeviceTokenResponse> tokens = authFeignClient.getDeviceTokens(request.getUserId());

        if (tokens == null || tokens.isEmpty()) {
            log.warn("No device tokens found for userId={}", request.getUserId());
            return;
        }

        for (DeviceTokenResponse deviceToken : tokens) {
            try {
                Message message = Message.builder()
                        .setToken(deviceToken.getFcmToken())
                        .setNotification(Notification.builder()
                                .setTitle("Atlas")
                                .setBody(request.getMessage())
                                .build())
                        .putData("jobId", request.getJobId().toString())
                        .putData("status", request.getStatus())
                        .build();

                String response = firebaseMessaging.send(message);
                log.info("Push sent to userId={} device={} response={}", request.getUserId(), deviceToken.getDeviceOs(), response);

            } catch (FirebaseMessagingException e) {
                String errorCode = e.getMessagingErrorCode() != null
                        ? e.getMessagingErrorCode().name()
                        : "UNKNOWN";

                if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                    log.warn("Stale token for userId={}, os={}: {}",
                            request.getUserId(), deviceToken.getDeviceOs(), errorCode);
                } else {
                    log.error("FCM send failed for userId={}: {} - {}",
                            request.getUserId(), errorCode, e.getMessage());
                }
            } catch (Exception e) {
                log.error("Unexpected error sending push to userId={}: {}", request.getUserId(), e.getMessage());
            }
        }
    }
}
