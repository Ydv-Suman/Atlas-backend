package com.atlas.agent_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "notification-service", url = "${atlas.notification-service.url}", path = "/api/notify/internal")
public interface NotificationFeignClient {

    @PostMapping("/push")
    void sendPush(@RequestBody Map<String, Object> notification);

    @PostMapping("/ws")
    void sendWebSocketUpdate(@RequestBody Map<String, Object> update);
}
