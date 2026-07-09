package com.atlas.workspace_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", url = "${atlas.auth-service.url}")
public interface AuthFeignClient {

    @GetMapping("/api/github/internal/token/{username}")
    String getGithubToken(@PathVariable("username") String username);
}
