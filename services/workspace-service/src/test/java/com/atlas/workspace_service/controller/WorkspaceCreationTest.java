package com.atlas.workspace_service.controller;

import com.atlas.shared.security.JwtClaims;
import com.atlas.workspace_service.config.WebConfig;
import com.atlas.workspace_service.dto.CreateProjectRequestDto;
import com.atlas.workspace_service.entity.RepoOwnership;
import com.atlas.workspace_service.exception.WorkspaceExceptionHandler;
import com.atlas.workspace_service.mapper.WorkspaceMapper;
import com.atlas.workspace_service.service.IWorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkspaceCreationTest {
    @Configuration @EnableWebMvc
    static class TestConfig {
        @Bean IWorkspaceService service() {
            var service = mock(IWorkspaceService.class);
            when(service.createProject(any(), eq("owner"))).thenAnswer(call -> {
                var mapper = new WorkspaceMapper();
                var entity = mapper.toEntity(call.getArgument(0), "owner");
                entity.setId(42L);
                return mapper.toDto(entity);
            });
            return service;
        }
        @Bean WorkspaceController controller(IWorkspaceService service) { return new WorkspaceController(service); }
        @Bean WorkspaceExceptionHandler errors() { return new WorkspaceExceptionHandler(); }
    }
    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, WebConfig.class); context.refresh();
        return context;
    }
    private String body(String ownership, String visibility) {
        return """
            {"projectName":"project","githubUrl":"https://github.com/owner/project","repoOwner":"owner",
             "repoOwnership":"%s","repoVisibility":"%s"}
            """.formatted(ownership, visibility);
    }
    @Test void acceptsSupportedValuesThroughControllerAndMapper() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            var authentication = UsernamePasswordAuthenticationToken.authenticated("owner", "", List.of());
            authentication.setDetails(new JwtClaims("user-id", "owner", List.of("ROLE_USER"), true, true, "FREE"));
            for (var ownership : RepoOwnership.values()) {
                mvc.perform(post("/api/workspace/projects").principal(authentication)
                    .contentType("application/json").content(body(ownership.name(), "PUBLIC")))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(42))
                    .andExpect(jsonPath("$.data.repoOwnership").value(ownership.name()));
            }
        }
    }
    @Test void rejectsUnsupportedAndBlankValuesBeforeCallingTheService() throws Exception {
        try (var context = context()) {
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            for (String ownership : new String[] {"PERSONAL", "ORGANIZATION", ""}) {
                mvc.perform(post("/api/workspace/projects").contentType("application/json").content(body(ownership, "PUBLIC")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorMessage").isNotEmpty());
            }
            mvc.perform(post("/api/workspace/projects").contentType("application/json").content(body("OWNER", "SECRET")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorMessage").value("Repo visibility must be PUBLIC or PRIVATE"));
            verifyNoInteractions(context.getBean(IWorkspaceService.class));
        }
    }
}
