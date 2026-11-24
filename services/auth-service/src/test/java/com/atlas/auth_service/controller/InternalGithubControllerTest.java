package com.atlas.auth_service.controller;

import com.atlas.auth_service.config.WebConfig;
import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import com.atlas.auth_service.service.IGithubOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalGithubControllerTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID DISCONNECTED_ID = UUID.randomUUID();

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean AtlasUserRespsitory repository() {
            var repository = mock(AtlasUserRespsitory.class);
            var connected = new AtlasUsers();
            connected.setUsername("connected-user");
            var disconnected = new AtlasUsers();
            disconnected.setUsername("disconnected-user");
            when(repository.findById(USER_ID)).thenReturn(Optional.of(connected));
            when(repository.findById(DISCONNECTED_ID)).thenReturn(Optional.of(disconnected));
            return repository;
        }
        @Bean IGithubOAuthService github() {
            var github = mock(IGithubOAuthService.class);
            when(github.getDecryptedToken("connected-user")).thenReturn(Optional.of("test-token"));
            when(github.getDecryptedToken("disconnected-user")).thenReturn(Optional.empty());
            return github;
        }
        @Bean InternalGithubController controller(AtlasUserRespsitory repository, IGithubOAuthService github) {
            return new InternalGithubController(repository, github);
        }
    }

    @Test
    void agentLookupResolvesUserIdToTheExistingUsernameTokenStore() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class, WebConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/api/auth/internal/github/token/" + USER_ID))
                    .andExpect(status().isOk()).andExpect(content().string("test-token"));
            mvc.perform(get("/api/auth/internal/github/token/" + DISCONNECTED_ID)).andExpect(status().isNotFound());
            mvc.perform(get("/api/auth/internal/github/token/" + UUID.randomUUID())).andExpect(status().isNotFound());
            verify(context.getBean(IGithubOAuthService.class), never()).getDecryptedToken(USER_ID.toString());
        }
    }
}
