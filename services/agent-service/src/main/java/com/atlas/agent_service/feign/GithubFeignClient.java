package com.atlas.agent_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "github-service", url = "${atlas.github-service.url}", path = "/api/github/internal")
public interface GithubFeignClient {

    @PostMapping("/clone")
    Map<String, Object> cloneRepo(@RequestParam String repoUrl, @RequestParam String token);

    @PostMapping("/push/{jobId}")
    Map<String, Object> pushChanges(@PathVariable String jobId, @RequestParam String token);

    @PostMapping("/pull-request")
    Map<String, Object> createPullRequest(@RequestBody Map<String, Object> prDetails);
}
