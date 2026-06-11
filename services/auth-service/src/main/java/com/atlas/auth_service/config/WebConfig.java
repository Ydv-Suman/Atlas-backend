package com.atlas.auth_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Help with configuring {@link HandlerMapping} path matching options such as
     * whether to use parsed {@code PathPatterns} or String pattern matching
     * with {@code PathMatcher}, whether to match trailing slashes, and more.
     *
     * @param configurer
     * @see PathMatchConfigurer
     * @since 4.0.3
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/atlas/v1", clazz -> true);
    }

}
