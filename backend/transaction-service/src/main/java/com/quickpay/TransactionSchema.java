package com.quickpay;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class TransactionSchema {
    @Bean
    ApplicationRunner initializeTransactionSchema(JdbcTemplate jdbc) {
        return args -> jdbc.execute("""
            CREATE TABLE IF NOT EXISTS transactions (
                transaction_id UUID PRIMARY KEY,
                payment_id UUID NOT NULL UNIQUE,
                customer_id BIGINT NOT NULL,
                merchant_name VARCHAR(120) NOT NULL,
                amount NUMERIC(12,2) NOT NULL,
                status VARCHAR(24) NOT NULL,
                created_at TIMESTAMPTZ NOT NULL
            )
            """);
    }
}
