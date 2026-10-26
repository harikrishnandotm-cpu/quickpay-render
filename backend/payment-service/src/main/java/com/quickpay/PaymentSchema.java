package com.quickpay;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class PaymentSchema {
    @Bean
    ApplicationRunner initializePaymentSchema(JdbcTemplate jdbc) {
        return args -> jdbc.execute("""
            CREATE TABLE IF NOT EXISTS payments (
                payment_id UUID PRIMARY KEY,
                customer_id BIGINT NOT NULL,
                merchant_id BIGINT NOT NULL,
                merchant_name VARCHAR(120) NOT NULL,
                amount NUMERIC(12,2) NOT NULL,
                status VARCHAR(24) NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
    }
}
