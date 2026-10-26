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

    private static RestClient clientFor(String baseUrl) {
        var factory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(40));
        return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();
    }
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<Object, Object> kafka;
    private final RestClient customerClient;
    private final RestClient merchantClient;

    @Autowired
    PaymentController(JdbcTemplate jdbc, KafkaTemplate<Object, Object> kafka, Environment env) {
        this(jdbc, kafka,
            clientFor(env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081")),
            clientFor(env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082")));
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
        java.util.List<Map<String, Object>> merchants;
        try {
            merchants = merchantClient.get().uri("/api/merchants").retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {});
        } catch (RestClientException ex) {
            log.error("Merchant service call failed", ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Merchant service is unavailable, please retry");
        }
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
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + ex.getResponseBodyAsString());
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

    record PaymentRequest(Long merchantId, BigDecimal amount) {}
}
