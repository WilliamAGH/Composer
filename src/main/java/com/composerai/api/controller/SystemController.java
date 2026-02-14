package com.composerai.api.controller;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SystemController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(statusBody("ok"));
    }

    @GetMapping("/chat/health")
    public ResponseEntity<Map<String, String>> chatHealth() {
        return ResponseEntity.ok(statusBody("UP"));
    }

    private Map<String, String> statusBody(String statusValue) {
        return Map.of(
                "status",
                statusValue,
                "service",
                "Composer API",
                "timestamp",
                LocalDateTime.now().toString());
    }
}
