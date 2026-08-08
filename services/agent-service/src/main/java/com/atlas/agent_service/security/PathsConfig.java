package com.atlas.agent_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/actuator/**",
                "/api/agent/webhook/**"
        );
    }

    @Bean(name = "internalPaths")
    public List<String> internalPaths() {
        return List.of(
                "/api/agent/internal/**"
        );
    }

    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of(
                "/api/agent/**"
        );
    }
}
