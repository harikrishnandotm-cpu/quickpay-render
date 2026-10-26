package com.quickpay;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
class PaymentController {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<Object, Object> kafka;
    private final RestClient customerClient;
    private final RestClient merchantClient;

    @Autowired
    PaymentController(JdbcTemplate jdbc, KafkaTemplate<Object, Object> kafka, Environment env) {
        this(jdbc, kafka,
            RestClient.builder()
                .baseUrl(env.getProperty("CUSTOMER_SERVICE_URL", "http://localhost:8081")).build(),
            RestClient.builder()
                .baseUrl(env.getProperty("MERCHANT_SERVICE_URL", "http://localhost:8082")).build());
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
        var merchants = merchantClient.get().uri("/api/merchants").retrieve()
            .body(new org.springframework.core.ParameterizedTypeReference<java.util.List<Map<String, Object>>>() {});
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
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment declined: " + ex.getResponseBodyAsString());
        } catch (org.springframework.web.client.RestClientException ex) {
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
        kafka.send("payment-events", paymentId.toString(), event);
        return Map.of("paymentId", paymentId, "status", "COMPLETED",
            "merchantName", merchantName, "amount", request.amount(),
            "message", "Demo payment completed. No external payment provider was charged.");
    }

    record PaymentRequest(Long merchantId, BigDecimal amount) {}
}
