package com.quickpay;

import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@RestController
class WarmupController {
    private static final long MIN_INTERVAL_MS = 30_000;

    private final Set<String> serviceUrls = new LinkedHashSet<>();
    private final HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private volatile long lastRun = 0;

    WarmupController(Environment env) {
        serviceUrls.add(env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081"));
        serviceUrls.add(env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082"));
        serviceUrls.add(env.getProperty("PAYMENT_SERVICE_URL", "http://localhost:8083"));
        serviceUrls.add(env.getProperty("TRANSACTION_SERVICE_URL", "http://localhost:8084"));
        serviceUrls.add(env.getProperty("NOTIFICATION_SERVICE_URL", "http://localhost:8085"));
    }

    @GetMapping("/api/warmup")
    Map<String, Object> warmup() {
        long now = System.currentTimeMillis();
        boolean started = false;
        if (now - lastRun >= MIN_INTERVAL_MS) {
            lastRun = now;
            started = true;
            for (String base : serviceUrls) {
                try {
                    HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/health"))
                        .timeout(Duration.ofSeconds(90))
                        .GET()
                        .build();
                    http.sendAsync(request, HttpResponse.BodyHandlers.discarding());
                } catch (RuntimeException ignored) {
                    // a bad URL must not break the endpoint
                }
            }
        }
        return Map.of("status", "UP", "warming", started);
    }
}
