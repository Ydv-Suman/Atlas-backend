package com.atlas.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/api/csrf/public",
                "/api/auth/login",
                "/api/users/register/public",
                "/api/users/verify-email",
                "/api/users/resend-otp",
                "/api/github/callback"
        );
    }


    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of(
                "/api/users/fetch",
                "/api/users/update",
                "/api/users/delete",
                "/api/auth/logout",
                "/api/github/authorize"
        );
    }


    @Bean(name = "adminPaths")
    public List<String> adminPaths() {
        return List.of(
                "/api/users/register/admin"
        );
    }
}
