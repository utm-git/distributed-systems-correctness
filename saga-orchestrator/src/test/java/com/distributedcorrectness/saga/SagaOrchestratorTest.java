package com.distributedcorrectness.saga;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class SagaOrchestratorTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderSagaService sagaService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("DELETE FROM shipments");
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM saga_state");
        jdbcTemplate.update("UPDATE inventory SET quantity = 100 WHERE item_id = 'ITEM_123'");
    }

    @Test
    void testSuccessfulSaga() {
        UUID orderId = sagaService.processOrder("ITEM_123", 5, new BigDecimal("50.00"), Collections.emptyMap());

        // Validate final state
        String state = jdbcTemplate.queryForObject("SELECT status FROM saga_state WHERE order_id = ?", String.class, orderId);
        assertEquals("COMPLETED", state);

        int quantity = jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE item_id = 'ITEM_123'", Integer.class);
        assertEquals(95, quantity);

        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payments WHERE order_id = ?", String.class, orderId);
        assertEquals("CHARGED", paymentStatus);

        String shipStatus = jdbcTemplate.queryForObject("SELECT status FROM shipments WHERE order_id = ?", String.class, orderId);
        assertEquals("SHIPPED", shipStatus);
    }

    @Test
    void testFailureAtCharge_RollsBackInventory() {
        assertThrows(RuntimeException.class, () -> {
            sagaService.processOrder("ITEM_123", 5, new BigDecimal("50.00"), Map.of("failCharge", true));
        });

        // The saga should be reported as FAILED
        String state = jdbcTemplate.queryForObject("SELECT status FROM saga_state LIMIT 1", String.class);
        assertEquals("FAILED", state);

        // Inventory should revert to 100
        int quantity = jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE item_id = 'ITEM_123'", Integer.class);
        assertEquals(100, quantity);

        // Payment should not exist since it failed
        int payments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class);
        assertEquals(0, payments);
    }

    @Test
    void testFailureAtShip_RollsBackPaymentAndInventory() {
        assertThrows(RuntimeException.class, () -> {
            sagaService.processOrder("ITEM_123", 10, new BigDecimal("100.00"), Map.of("failShip", true));
        });

        // Saga FAILED
        String state = jdbcTemplate.queryForObject("SELECT status FROM saga_state LIMIT 1", String.class);
        assertEquals("FAILED", state);

        // Inventory reverted
        int quantity = jdbcTemplate.queryForObject("SELECT quantity FROM inventory WHERE item_id = 'ITEM_123'", Integer.class);
        assertEquals(100, quantity);

        // Payment was successful but then compensated (Refunded)
        String paymentStatus = jdbcTemplate.queryForObject("SELECT status FROM payments LIMIT 1", String.class);
        assertEquals("REFUNDED", paymentStatus);

        // Shipment does not exist
        int shipments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shipments", Integer.class);
        assertEquals(0, shipments);
    }
}
