package com.quickpay;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class HealthController {
    private final Environment environment;

    HealthController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    Map<String, String> health() {
        return Map.of("status", "UP", "service", environment.getProperty("SERVICE_NAME", "quickpay"));
    }
}
