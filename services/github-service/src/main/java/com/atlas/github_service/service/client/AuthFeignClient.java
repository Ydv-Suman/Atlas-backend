package com.atlas.github_service.service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", url = "${app.auth-service.url}")
public interface AuthFeignClient {

    @PostMapping("/api/auth/github-authorized")
    void setGithubAuthorized(@RequestParam("email") String email);
}

