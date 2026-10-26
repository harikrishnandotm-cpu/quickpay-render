package com.quickpay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class TransactionController {
    private final JdbcTemplate jdbc;

    TransactionController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/transactions")
    List<Map<String, Object>> transactions(@RequestHeader("X-Customer-Id") long customerId) {
        return jdbc.queryForList("""
            SELECT transaction_id AS id, payment_id AS "paymentId", merchant_name AS "merchantName",
                   amount, status, created_at AS "createdAt"
            FROM transactions WHERE customer_id = ? ORDER BY created_at DESC LIMIT 100
            """, customerId);
    }
}

@Component
class TransactionConsumer {
    private final JdbcTemplate jdbc;

    TransactionConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "payment-events")
    void onPayment(Map<String, Object> event) {
        UUID paymentId = UUID.fromString((String) event.get("paymentId"));
        jdbc.update("""
            INSERT INTO transactions (transaction_id, payment_id, customer_id, merchant_name, amount, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (payment_id) DO NOTHING
            """, UUID.randomUUID(), paymentId, ((Number) event.get("customerId")).longValue(),
            event.get("merchantName"), new BigDecimal(event.get("amount").toString()), event.get("status"));
    }
}
