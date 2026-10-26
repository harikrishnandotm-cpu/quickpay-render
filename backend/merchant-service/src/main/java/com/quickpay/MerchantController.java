package com.quickpay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
class MerchantController {
    private final JdbcTemplate jdbc;

    MerchantController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/merchants")
    List<Map<String, Object>> merchants() {
        return jdbc.queryForList("""
            SELECT merchant_id AS id, merchant_name AS name, category, email
            FROM merchants WHERE status = 'ACTIVE' ORDER BY merchant_id
            """);
    }
}
