package com.atlas.agent_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-service", url = "${atlas.auth-service.url}", path = "/api/auth/internal")
public interface AuthFeignClient {

    @PostMapping("/credits/consume")
    void consumeCredits(@RequestParam UUID userId, @RequestParam int amount);

    @GetMapping("/device-token/{userId}")
    List<String> getDeviceTokens(@PathVariable UUID userId);

    @GetMapping("/github/token/{userId}")
    String getGithubToken(@PathVariable UUID userId);
}
