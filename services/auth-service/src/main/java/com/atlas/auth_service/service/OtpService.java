package com.atlas.auth_service.service;

import com.atlas.auth_service.exception.OtpException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class OtpService {

    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp_attempts:";
    private static final String COOLDOWN_KEY_PREFIX = "otp_cooldown:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String generateAndStoreOtp(String email) {
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        String otpKey = OTP_KEY_PREFIX + email;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;

        redisTemplate.opsForValue().set(otpKey, otp, OTP_TTL);
        redisTemplate.delete(attemptsKey);
        redisTemplate.opsForValue().set(cooldownKey, "1", RESEND_COOLDOWN);

        return otp;
    }

    public void verifyOtp(String email, String otp) {
        String attemptsKey = ATTEMPTS_KEY_PREFIX + email;
        String otpKey = OTP_KEY_PREFIX + email;

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(attemptsKey, OTP_TTL);
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new OtpException("Maximum verification attempts exceeded. Please request a new OTP");
        }

        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            throw new OtpException("OTP has expired or does not exist. Please request a new one");
        }

        if (!storedOtp.equals(otp)) {
            throw new OtpException("Invalid OTP. " + (MAX_ATTEMPTS - attempts) + " attempts remaining");
        }

        // OTP verified — clean up
        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);
        redisTemplate.delete(COOLDOWN_KEY_PREFIX + email);
    }

    public void checkResendCooldown(String email) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + email;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new OtpException("Please wait 60 seconds before requesting a new OTP");
        }
    }
}
