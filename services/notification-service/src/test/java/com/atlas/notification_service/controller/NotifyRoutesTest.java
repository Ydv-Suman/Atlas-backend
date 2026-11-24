package com.atlas.notification_service.controller;

import com.atlas.notification_service.config.SecurityConfig;
import com.atlas.notification_service.config.WebConfig;
import com.atlas.notification_service.dto.NotifyRequest;
import com.atlas.notification_service.service.NotificationService;
import com.atlas.notification_service.websocket.WebSocketSessionRegistry;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotifyRoutesTest {
    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean NotificationService service() { return mock(NotificationService.class); }
        @Bean WebSocketSessionRegistry registry() { return mock(WebSocketSessionRegistry.class); }
        @Bean NotifyController controller(NotificationService service, WebSocketSessionRegistry registry) {
            return new NotifyController(service, registry);
        }
    }

    @Test
    void internalNotificationRoutesMatchTheirSecurityRules() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(TestConfig.class, WebConfig.class, SecurityConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            String jobId = "11111111-1111-4111-8111-111111111111";
            mvc.perform(post("/api/notify/ws").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobId\":\"" + jobId + "\",\"step\":\"FAILED\",\"status\":\"FAILED\",\"message\":\"Job failed\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/notify").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"jobId\":\"" + jobId + "\",\"userId\":\"" + jobId + "\",\"status\":\"FAILED\",\"message\":\"Job failed\"}"))
                    .andExpect(status().isOk());
            mvc.perform(post("/notify/ws").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            verify(context.getBean(WebSocketSessionRegistry.class)).sendUpdate(eq(jobId), any());
            verify(context.getBean(NotificationService.class)).sendPush(any(NotifyRequest.class));
        }
    }
}
