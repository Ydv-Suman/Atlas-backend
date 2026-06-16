package com.atlas.auth_service.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class PathsConfig {

    @Bean(name = "publicPaths")
    public List<String> publicPaths() {
        return List.of(
                "/api/v1/csrf/public",
                "/api/v1/auth/login",
                "/api/v1/users/register/public",
                "/api/v1/users/verify-email",
                "/api/v1/users/resend-otp"
        );
    }


    @Bean(name = "securePaths")
    public List<String> securePaths() {
        return List.of(
                "/api/v1/users/fetch",
                "/api/v1/users/update",
                "/api/v1/users/delete",
                "/api/v1/auth/logout"
        );
    }


    @Bean(name = "adminPaths")
    public List<String> adminPaths() {
        return List.of(
                "/api/v1/users/register/admin"
        );
    }
}
