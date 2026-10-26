package com.quickpay;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class CustomerSchema {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    ApplicationRunner initializeCustomerSchema(JdbcTemplate jdbc) {
        return args -> {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS accounts (
                    customer_id BIGSERIAL PRIMARY KEY,
                    customer_name VARCHAR(120) NOT NULL,
                    email VARCHAR(254) NOT NULL UNIQUE,
                    phone_number VARCHAR(32) NOT NULL,
                    account_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                    available_balance NUMERIC(12,2) NOT NULL DEFAULT 1000.00 CHECK (available_balance >= 0),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS customer_credentials (
                    customer_id BIGINT PRIMARY KEY REFERENCES accounts(customer_id) ON DELETE CASCADE,
                    password_hash VARCHAR(100) NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        };
    }
}
