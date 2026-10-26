package com.quickpay;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
class CustomerController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    CustomerController(JdbcTemplate jdbc, PasswordEncoder encoder, JwtService jwt) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/api/auth/signup")
    @Transactional
    Map<String, Object> signup(@RequestBody SignupRequest request) {
        if (request.name() == null || request.name().isBlank() || request.name().length() > 120
            || request.phone() == null || request.phone().isBlank() || request.phone().length() > 32
            || request.email() == null || !EMAIL.matcher(request.email()).matches()
            || request.password() == null || request.password().length() < 10
            || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Enter a name, valid email, phone, and password (10–72 characters)");
        }
        String email = request.email().trim().toLowerCase();
        Long id = jdbc.queryForObject(
            "INSERT INTO accounts (customer_name, email, phone_number) VALUES (?, ?, ?) RETURNING customer_id",
            Long.class, request.name().trim(), email, request.phone().trim());
        jdbc.update("INSERT INTO customer_credentials (customer_id, password_hash) VALUES (?, ?)",
            id, encoder.encode(request.password()));
        return authResponse(id, request.name().trim(), email, new BigDecimal("1000.00"));
    }

    @PostMapping("/api/auth/login")
    Map<String, Object> login(@RequestBody LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            throw new IllegalArgumentException("Email and password are required");
        }
        var rows = jdbc.query("""
            SELECT a.customer_id, a.customer_name, a.email, a.available_balance, c.password_hash
            FROM accounts a JOIN customer_credentials c USING (customer_id)
            WHERE a.email = ? AND a.account_status = 'ACTIVE'
            """, (rs, row) -> new LoginRecord(
                rs.getLong("customer_id"), rs.getString("customer_name"), rs.getString("email"),
                rs.getBigDecimal("available_balance"), rs.getString("password_hash")),
            request.email().trim().toLowerCase());
        if (rows.isEmpty() || !encoder.matches(request.password(), rows.get(0).passwordHash())) {
            throw new IllegalArgumentException("Invalid email or password");
        }
        LoginRecord customer = rows.get(0);
        return authResponse(customer.id(), customer.name(), customer.email(), customer.balance());
    }

    @GetMapping("/api/customers/me")
    Map<String, Object> me(@RequestHeader("X-Customer-Id") long customerId) {
        return jdbc.queryForMap("""
            SELECT customer_id AS id, customer_name AS name, email, phone_number AS phone,
                   account_status AS status, available_balance AS balance
            FROM accounts WHERE customer_id = ?
            """, customerId);
    }

    @PostMapping("/internal/accounts/{customerId}/debit")
    @Transactional
    Map<String, Object> debit(@PathVariable long customerId, @RequestBody DebitRequest request) {
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0
            || request.amount().scale() > 2 || request.amount().compareTo(new BigDecimal("100000.00")) > 0) {
            throw new IllegalArgumentException("Payment amount must be between 0.01 and 100000.00");
        }
        int updated = jdbc.update("""
            UPDATE accounts SET available_balance = available_balance - ?, updated_at = CURRENT_TIMESTAMP
            WHERE customer_id = ? AND account_status = 'ACTIVE' AND available_balance >= ?
            """, request.amount(), customerId, request.amount());
        if (updated == 0) {
            throw new IllegalArgumentException("Account not found, inactive, or insufficient balance");
        }
        return jdbc.queryForMap("SELECT available_balance AS balance FROM accounts WHERE customer_id = ?", customerId);
    }

    private Map<String, Object> authResponse(long id, String name, String email, BigDecimal balance) {
        return Map.of("token", jwt.issue(id, email), "customer",
            Map.of("id", id, "name", name, "email", email, "balance", balance));
    }

    record SignupRequest(String name, String email, String phone, String password) {}
    record LoginRequest(String email, String password) {}
    record DebitRequest(BigDecimal amount) {}
    record LoginRecord(long id, String name, String email, BigDecimal balance, String passwordHash) {}
}
