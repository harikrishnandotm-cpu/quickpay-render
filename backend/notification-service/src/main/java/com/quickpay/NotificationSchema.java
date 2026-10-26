package com.quickpay;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class NotificationSchema {
    @Bean
    ApplicationRunner initializeNotificationSchema(JdbcTemplate jdbc) {
        return args -> jdbc.execute("""
            CREATE TABLE IF NOT EXISTS notifications (
                notification_id BIGSERIAL PRIMARY KEY,
                payment_id UUID NOT NULL,
                customer_id BIGINT NOT NULL,
                message VARCHAR(240) NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }
}
