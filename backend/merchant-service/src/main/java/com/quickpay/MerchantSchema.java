package com.quickpay;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
class MerchantSchema {
    @Bean
    ApplicationRunner initializeMerchantSchema(JdbcTemplate jdbc) {
        return args -> {
            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS merchants (
                    merchant_id BIGSERIAL PRIMARY KEY,
                    merchant_name VARCHAR(120) NOT NULL,
                    category VARCHAR(80) NOT NULL,
                    email VARCHAR(254) NOT NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE'
                )
                """);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM merchants", Integer.class) == 0) {
                jdbc.update("INSERT INTO merchants (merchant_name, category, email) VALUES (?, ?, ?)",
                    "Northstar Coffee", "Food & Drink", "hello@northstar.example");
                jdbc.update("INSERT INTO merchants (merchant_name, category, email) VALUES (?, ?, ?)",
                    "Paper & Pine", "Shopping", "team@paperpine.example");
                jdbc.update("INSERT INTO merchants (merchant_name, category, email) VALUES (?, ?, ?)",
                    "CityCycle", "Transport", "help@citycycle.example");
            }
        };
    }
}
