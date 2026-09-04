package com.atlas.agent_service.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "workspace-service", url = "${atlas.workspace-service.url}", path = "/api/workspace/internal")
public interface WorkspaceFeignClient {

    @GetMapping("/project/{projectId}")
    Map<String, Object> getProjectDetails(@PathVariable Long projectId);

    @GetMapping("/project/{projectId}/repo-url")
    String getRepoUrl(@PathVariable Long projectId);
}
