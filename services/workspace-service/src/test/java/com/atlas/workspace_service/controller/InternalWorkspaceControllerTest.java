package com.atlas.workspace_service.controller;

import com.atlas.workspace_service.config.WebConfig;
import com.atlas.workspace_service.entity.WorkspaceEntity;
import com.atlas.workspace_service.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InternalWorkspaceControllerTest {
    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean WorkspaceRepository repository() {
            var repository = mock(WorkspaceRepository.class);
            var project = new WorkspaceEntity();
            project.setGithubUrl("https://github.com/atlas/project");
            when(repository.findById(7L)).thenReturn(Optional.of(project));
            return repository;
        }
        @Bean InternalWorkspaceController controller(WorkspaceRepository repository) {
            return new InternalWorkspaceController(repository);
        }
    }

    @Test
    void repoLookupUsesExactlyOneApiPrefix() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class, WebConfig.class);
            context.refresh();
            MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(get("/api/workspace/internal/project/7/repo-url"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("https://github.com/atlas/project"));
        }
    }
}
