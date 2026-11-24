package com.atlas.agent_service.controller;

import com.atlas.shared.dto.ErrorResponse;
import com.atlas.shared.exception.InsufficientCreditsException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AgentExceptionHandler {
    @ExceptionHandler(InsufficientCreditsException.class)
    public ResponseEntity<ErrorResponse> insufficientCredits(InsufficientCreditsException error, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ErrorResponse.of(HttpStatus.PAYMENT_REQUIRED, error.getMessage(), request.getRequestURI()));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> requestError(org.springframework.web.server.ResponseStatusException error, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return ResponseEntity.status(status).body(ErrorResponse.of(status,
                error.getReason() == null ? status.getReasonPhrase() : error.getReason(), request.getRequestURI()));
    }

}
