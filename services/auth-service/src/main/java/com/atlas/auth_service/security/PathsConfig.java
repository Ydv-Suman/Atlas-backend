package com.atlas.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/atlas/v1/register/public"
        );
    }


    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of();
    }


    @Bean(name = "adminPaths")
    public List<String> adminPaths() {
        return List.of(
                "/atlas/v1/admin/users"
        );
    }
}
