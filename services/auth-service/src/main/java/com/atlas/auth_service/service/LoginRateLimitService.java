package com.atlas.auth_service.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginRateLimitService {

    private static final String LOGIN_ATTEMPTS_KEY_PREFIX = "login_attempts:";
    private static final int MAX_LOGIN_ATTEMPTS = 10;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public LoginRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkRateLimit(String username) {
        String key = LOGIN_ATTEMPTS_KEY_PREFIX + username;
        String value = redisTemplate.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= MAX_LOGIN_ATTEMPTS) {
            throw new com.atlas.auth_service.exception.RateLimitException(
                    "Too many login attempts. Please try again later");
        }
    }

    public void recordFailedAttempt(String username) {
        String key = LOGIN_ATTEMPTS_KEY_PREFIX + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(key, LOCKOUT_DURATION);
        }
    }

    public void resetAttempts(String username) {
        redisTemplate.delete(LOGIN_ATTEMPTS_KEY_PREFIX + username);
    }
}
