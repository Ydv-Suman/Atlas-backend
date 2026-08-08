package com.atlas.auth_service.controller;

import com.atlas.auth_service.entity.AtlasUsers;
import com.atlas.auth_service.repository.AtlasUserRespsitory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/auth/internal/credits")
@RequiredArgsConstructor
public class CreditController {

    private final AtlasUserRespsitory userRepository;

    @GetMapping("/balance/{userId}")
    public ResponseEntity<Integer> getCreditBalance(@PathVariable UUID userId) {
        AtlasUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(user.getCredits());
    }

    @PostMapping("/consume")
    public ResponseEntity<Void> consumeCredits(
            @RequestParam UUID userId,
            @RequestParam int amount) {

        AtlasUsers user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getCredits() < amount) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient credits. Balance: " + user.getCredits());
        }

        user.setCredits(user.getCredits() - amount);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}
