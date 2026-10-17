package com.nano.clinicbooking.controller.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {
    private final ApplicationAvailability availability;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return ResponseEntity.status(503).body(Map.of("status", "STARTING"));
        }
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
