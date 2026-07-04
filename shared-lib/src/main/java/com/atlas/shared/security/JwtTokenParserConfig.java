package com.atlas.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures JwtTokenParser from app.jwt.secret property.
 * Services get JWT parsing for free by adding shared-lib + setting the property.
 */
@AutoConfiguration
@ConditionalOnProperty(name = "app.jwt.secret")
public class JwtTokenParserConfig {

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenParser jwtTokenParser(@Value("${app.jwt.secret}") String secret) {
        return new JwtTokenParser(secret);
    }
}
