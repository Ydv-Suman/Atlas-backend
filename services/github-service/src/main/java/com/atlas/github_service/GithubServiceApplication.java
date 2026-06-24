package com.atlas.github_service;

import com.atlas.github_service.config.GithubOAuthConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(GithubOAuthConfig.class)
public class GithubServiceApplication {

	public static void main(String[] args) {

		SpringApplication.run(GithubServiceApplication.class, args);
	}

}
