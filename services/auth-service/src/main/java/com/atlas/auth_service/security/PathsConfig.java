package com.atlas.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/atlas/v1/csrf/public",
                "/atlas/v1/auth/login",
                "/atlas/v1/users/register/public"
        );
    }


    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of(
                "/atlas/v1/users/fetch",
                "/atlas/v1/users/update",
                "/atlas/v1/users/delete"
        );
    }


    @Bean(name = "adminPaths")
    public List<String> adminPaths() {
        return List.of(
                "/atlas/v1/users/register/admin"
        );
    }
}
