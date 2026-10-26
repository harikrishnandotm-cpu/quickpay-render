package com.quickpay;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentControllerTest {
    private static final long CUSTOMER_ID = 42L;
    private static final long MERCHANT_ID = 7L;
    private static final String MERCHANT_NAME = "Northstar Coffee";
    private static final BigDecimal AMOUNT = new BigDecimal("12.34");

    private JdbcTemplate jdbc;
    private KafkaTemplate<Object, Object> kafka;
    private RestClient customerClient;
    private RestClient merchantClient;
    private PaymentController controller;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        kafka = mock(KafkaTemplate.class);
        customerClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        merchantClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        controller = new PaymentController(jdbc, kafka, customerClient, merchantClient);

        List<Map<String, Object>> merchants = List.of(Map.of(
            "id", MERCHANT_ID,
            "name", MERCHANT_NAME));
        when(merchantClient.get().uri("/api/merchants").retrieve()
            .body(any(ParameterizedTypeReference.class))).thenReturn(merchants);
        when(customerClient.post()
            .uri("/internal/accounts/{id}/debit", CUSTOMER_ID)
            .body(any())
            .retrieve()
            .toBodilessEntity()).thenReturn(ResponseEntity.ok().build());
        clearInvocations(jdbc, kafka, customerClient, merchantClient);
    }

    @Test
    void recordsCompletedPaymentForSelectedMerchantAndAmount() {
        Map<String, Object> result = controller.pay(
            CUSTOMER_ID, new PaymentController.PaymentRequest(MERCHANT_ID, AMOUNT));

        assertThat(result)
            .containsEntry("status", "COMPLETED")
            .containsEntry("merchantName", MERCHANT_NAME)
            .containsEntry("amount", AMOUNT);
        verify(customerClient.post()
            .uri("/internal/accounts/{id}/debit", CUSTOMER_ID)
            .body(eq(Map.of("amount", AMOUNT)))
            .retrieve()).toBodilessEntity();
        verify(jdbc).update(
            argThat(sql -> sql.contains("INSERT INTO payments") && sql.contains("'COMPLETED'")),
            any(),
            eq(CUSTOMER_ID),
            eq(MERCHANT_ID),
            eq(MERCHANT_NAME),
            eq(AMOUNT));
        verify(kafka).send(
            eq("payment-events"),
            anyString(),
            argThat(event -> event instanceof Map<?, ?> paymentEvent
                && paymentEvent.get("customerId").equals(CUSTOMER_ID)
                && paymentEvent.get("merchantName").equals(MERCHANT_NAME)
                && paymentEvent.get("amount").equals(AMOUNT)
                && paymentEvent.get("status").equals("COMPLETED")));
    }

    @Test
    void rejectsNonPositivePaymentAmountWithoutCallingServices() {
        assertThatThrownBy(() -> controller.pay(
            CUSTOMER_ID, new PaymentController.PaymentRequest(MERCHANT_ID, BigDecimal.ZERO)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("between 0.01 and 100000.00");

        verifyNoInteractions(jdbc, kafka, customerClient, merchantClient);
    }
}
