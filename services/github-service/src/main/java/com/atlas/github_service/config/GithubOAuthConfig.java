package com.atlas.github_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.github")
@Getter
@Setter
public class GithubOAuthConfig {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String scopes;
}
