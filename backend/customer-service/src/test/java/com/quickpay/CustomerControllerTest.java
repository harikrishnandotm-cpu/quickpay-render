package com.quickpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerControllerTest {
    private static final long CUSTOMER_ID = 42L;

    private JdbcTemplate jdbc;
    private CustomerController controller;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:customer-test;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS accounts");
        jdbc.execute("""
            CREATE TABLE accounts (
                customer_id BIGINT PRIMARY KEY,
                account_status VARCHAR(24) NOT NULL,
                available_balance DECIMAL(12, 2) NOT NULL,
                updated_at TIMESTAMP
            )
            """);
        jdbc.update("""
            INSERT INTO accounts (customer_id, account_status, available_balance)
            VALUES (?, 'ACTIVE', ?)
            """, CUSTOMER_ID, new BigDecimal("1000.00"));
        controller = new CustomerController(
            jdbc,
            new BCryptPasswordEncoder(4),
            new JwtService("unit-test-secret-which-is-long-enough-for-hmac"));
    }

    @Test
    void debitingPaymentReducesCustomerBalanceByExactAmount() {
        var result = controller.debit(CUSTOMER_ID, new CustomerController.DebitRequest(new BigDecimal("12.34")));

        assertThat(result.get("balance")).isEqualTo(new BigDecimal("987.66"));
        assertThat(jdbc.queryForObject(
            "SELECT available_balance FROM accounts WHERE customer_id = ?", BigDecimal.class, CUSTOMER_ID))
            .isEqualByComparingTo("987.66");
    }

    @Test
    void insufficientBalanceDoesNotChangeCustomerBalance() {
        assertThatThrownBy(() -> controller.debit(
            CUSTOMER_ID, new CustomerController.DebitRequest(new BigDecimal("1000.01"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("insufficient balance");

        assertThat(jdbc.queryForObject(
            "SELECT available_balance FROM accounts WHERE customer_id = ?", BigDecimal.class, CUSTOMER_ID))
            .isEqualByComparingTo("1000.00");
    }
}
