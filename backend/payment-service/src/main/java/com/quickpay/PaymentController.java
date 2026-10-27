package com.quickpay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private static RestClient clientFor(String baseUrl, String internalKey) {
        log.info("Payment service HTTP client base URL: {}", baseUrl);
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(40));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory).baseUrl(baseUrl);
        if (internalKey != null && !internalKey.isBlank()) {
            builder.defaultHeader("X-Internal-Key", internalKey);
        }
        return builder.build();
    }

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<Object, Object> kafka;
    private final RestClient customerClient;
    private final RestClient merchantClient;
    private String merchantUrl = "unknown";

    @Autowired
    PaymentController(JdbcTemplate jdbc, KafkaTemplate<Object, Object> kafka, Environment env) {
        this(jdbc, kafka,
            clientFor(env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081"),
                env.getProperty("INTERNAL_API_KEY", "")),
            clientFor(env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082"),
                env.getProperty("INTERNAL_API_KEY", "")));
        this.merchantUrl = env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082");
    }

    PaymentController(JdbcTemplate jdbc, KafkaTemplate<Object, Object> kafka,
                      RestClient customerClient, RestClient merchantClient) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.customerClient = customerClient;
        this.merchantClient = merchantClient;
    }

    @PostMapping("/api/payments")
    Map<String, Object> pay(@RequestHeader("X-Customer-Id") long customerId, @RequestBody PaymentRequest request) {
        if (request.merchantId() == null || request.amount() == null
            || request.amount().compareTo(BigDecimal.ZERO) <= 0 || request.amount().scale() > 2
            || request.amount().compareTo(new BigDecimal("100000.00")) > 0) {
            throw new IllegalArgumentException("Choose a merchant and enter an amount between 0.01 and 100000.00");
        }
        java.util.List<Map<String, Object>> merchants = fetchMerchants();
        Map<String, Object> merchant = merchants == null ? null : merchants.stream()
            .filter(item -> String.valueOf(item.get("id")).equals(request.merchantId().toString()))
            .findFirst().orElse(null);
        if (merchant == null) throw new IllegalArgumentException("Selected merchant is unavailable");
        String merchantName = String.valueOf(merchant.get("name"));
        UUID paymentId = UUID.randomUUID();
        try {
            customerClient.post().uri("/internal/accounts/{id}/debit", customerId)
                .body(Map.of("amount", request.amount())).retrieve().toBodilessEntity();
        } catch (org.springframework.web.client.RestClientResponseException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                log.error("Customer service returned {}", ex.getStatusCode(), ex);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Customer account service is unavailable, please retry");
            }
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + errorText(ex.getResponseBodyAsString()));
        } catch (org.springframework.web.client.RestClientException ex) {
            log.error("Customer service call failed", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Customer account service is unavailable");
        }
        jdbc.update("""
            INSERT INTO payments (payment_id, customer_id, merchant_id, merchant_name, amount, status)
            VALUES (?, ?, ?, ?, ?, 'COMPLETED')
            """, paymentId, customerId, request.merchantId(), merchantName, request.amount());
        Map<String, Object> event = Map.of(
            "paymentId", paymentId.toString(), "customerId", customerId,
            "merchantName", merchantName, "amount", request.amount(),
            "status", "COMPLETED");
        try {
            kafka.send("payment-events", paymentId.toString(), event);
        } catch (Exception ex) {
            // Payment is already recorded; do not fail the request because notifications are down
            log.warn("Could not publish payment event {}", paymentId, ex);
        }
        return Map.of("paymentId", paymentId, "status", "COMPLETED",
            "merchantName", merchantName, "amount", request.amount(),
            "message", "Demo payment completed. No external payment provider was charged.");
    }

    /** The merchant service may be waking up on a free host, so try a few times before giving up. */
    private java.util.List<Map<String, Object>> fetchMerchants() {
        RestClientException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return merchantClient.get().uri("/api/merchants").retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {});
            } catch (RestClientException ex) {
                last = ex;
                log.warn("Merchant lookup attempt {} failed: {}", attempt, ex.getMessage());
                if (attempt < 3) {
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Merchant service call failed", last);
        String detail = last == null ? "no response" : String.valueOf(last.getMessage());
        if (detail.length() > 160) {
            detail = detail.substring(0, 160);
        }
        // DEBUG: remove the bracketed part once the problem is fixed
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Merchant service is unavailable, please retry [debug: " + merchantUrl + " -> " + detail + "]");
    }

    private static String errorText(String body) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"error\"\\s*:\\s*\"([^\"]*)\"").matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : "the payment could not be completed";
    }

    record PaymentRequest(Long merchantId, BigDecimal amount) {}
}
