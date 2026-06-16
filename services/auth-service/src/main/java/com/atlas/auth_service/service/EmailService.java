package com.atlas.auth_service.service;

import com.atlas.auth_service.exception.EmailSendingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public EmailService(JavaMailSender mailSender, @Value("${app.otp.from-email}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    /**
     * Async — used during registration. Does not block caller.
     * If sending fails, user can use /resend-otp.
     */
    private static final int MAX_ASYNC_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 2000;

    @Async
    public void sendOtpEmailAsync(String toEmail, String otp) {
        for (int attempt = 1; attempt <= MAX_ASYNC_RETRIES; attempt++) {
            try {
                doSendOtpEmail(toEmail, otp);
                return;
            } catch (Exception e) {
                log.error("Async OTP email attempt {}/{} failed for {}: ", attempt, MAX_ASYNC_RETRIES, toEmail, e);
                if (attempt < MAX_ASYNC_RETRIES) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        log.error("All {} async OTP email attempts failed for {}. User must use /resend-otp", MAX_ASYNC_RETRIES, toEmail);
    }

    /**
     * Synchronous — used by /resend-otp so user gets immediate feedback.
     * Throws EmailSendingException on failure → 503 via GlobalExceptionHandler.
     */
    public void sendOtpEmail(String toEmail, String otp) {
        try {
            doSendOtpEmail(toEmail, otp);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: ", toEmail, e);
            throw new EmailSendingException("Failed to send verification email. Please try again later.", e);
        }
    }

    private void doSendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Atlas - Email Verification Code");
        message.setText(
                "Your verification code is: " + otp + "\n\n" +
                "This code expires in 15 minutes.\n" +
                "If you did not request this, please ignore this email."
        );
        mailSender.send(message);
        log.info("OTP email sent to: {}", toEmail);
    }
}
