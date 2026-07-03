package com.risk.dupdetect.controller;

import com.risk.dupdetect.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

/**
 * Admin controller for dynamically configuring the detection system.
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final SystemConfigService configService;

    @Autowired
    public ConfigController(SystemConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/window")
    public ResponseEntity<Map<String, Object>> getWindowConfig() {
        return ResponseEntity.ok(Map.of("windowSeconds", configService.getWindowSeconds()));
    }

    @PutMapping("/window")
    public ResponseEntity<Map<String, Object>> updateWindowConfig(@RequestBody Map<String, Long> body) {
        long seconds = body.getOrDefault("windowSeconds", 60L);
        configService.setWindowSeconds(seconds);
        return ResponseEntity.ok(Map.of("windowSeconds", configService.getWindowSeconds()));
    }

    @GetMapping("/identity-fields")
    public ResponseEntity<Map<String, Object>> getIdentityFields() {
        return ResponseEntity.ok(Map.of("identityFields", configService.getIdentityFields()));
    }

    @PutMapping("/identity-fields")
    public ResponseEntity<Map<String, Object>> updateIdentityFields(@RequestBody Map<String, Set<String>> body) {
        Set<String> fields = body.get("identityFields");
        if (fields != null && !fields.isEmpty()) {
            configService.setIdentityFields(fields);
        }
        return ResponseEntity.ok(Map.of("identityFields", configService.getIdentityFields()));
    }

    @GetMapping("/amount-tolerance")
    public ResponseEntity<Map<String, Object>> getAmountTolerance() {
        return ResponseEntity.ok(Map.of("amountTolerance", configService.getAmountTolerance()));
    }

    @PutMapping("/amount-tolerance")
    public ResponseEntity<Map<String, Object>> updateAmountTolerance(@RequestBody Map<String, BigDecimal> body) {
        BigDecimal tolerance = body.getOrDefault("amountTolerance", BigDecimal.ZERO);
        configService.setAmountTolerance(tolerance);
        return ResponseEntity.ok(Map.of("amountTolerance", configService.getAmountTolerance()));
    }
}
