package com.atlas.workspace_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/actuator/health"
        );
    }

    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of(
                "/api/workspace/**"
        );
    }
}
