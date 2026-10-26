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
class NotificationController {
    private final JdbcTemplate jdbc;

    NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/notifications")
    List<Map<String, Object>> notifications(@RequestHeader("X-Customer-Id") long customerId) {
        return jdbc.queryForList("""
            SELECT notification_id AS id, payment_id AS "paymentId", message, created_at AS "createdAt"
            FROM notifications WHERE customer_id = ? ORDER BY created_at DESC LIMIT 50
            """, customerId);
    }
}

@Component
class NotificationConsumer {
    private final JdbcTemplate jdbc;

    NotificationConsumer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @KafkaListener(topics = "payment-events")
    void onPayment(Map<String, Object> event) {
        UUID paymentId = UUID.fromString((String) event.get("paymentId"));
        long customerId = ((Number) event.get("customerId")).longValue();
        String message = "Payment of " + new BigDecimal(event.get("amount").toString()).toPlainString()
            + " to " + event.get("merchantName") + " completed.";
        jdbc.update("""
            INSERT INTO notifications (payment_id, customer_id, message)
            SELECT ?, ?, ? WHERE NOT EXISTS (SELECT 1 FROM notifications WHERE payment_id = ?)
            """, paymentId, customerId, message, paymentId);
    }
}
