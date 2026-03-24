package com.distributedcorrectness.saga;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderSagaService {

    private final JdbcTemplate jdbcTemplate;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;

    public OrderSagaService(JdbcTemplate jdbcTemplate, InventoryService inventoryService, 
                            PaymentService paymentService, ShippingService shippingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
    }

    public UUID processOrder(String itemId, int quantity, BigDecimal amount, Map<String, Boolean> flags) {
        UUID orderId = UUID.randomUUID();
        updateState(orderId, "PENDING", "INIT");

        try {
            // Step 1: Reserve Inventory
            updateState(orderId, "PENDING", "RESERVING_INVENTORY");
            inventoryService.reserve(itemId, quantity, flags.getOrDefault("failReserve", false));

            // Step 2: Charge Payment
            updateState(orderId, "PENDING", "CHARGING");
            try {
                paymentService.charge(orderId, amount, flags.getOrDefault("failCharge", false));
            } catch (Exception e) {
                // Compensate Step 1
                updateState(orderId, "COMPENSATING", "RELEASING_INVENTORY");
                inventoryService.release(itemId, quantity);
                throw e;
            }

            // Step 3: Ship Order
            updateState(orderId, "PENDING", "SHIPPING");
            try {
                shippingService.ship(orderId, "123 Main St", flags.getOrDefault("failShip", false));
            } catch (Exception e) {
                // Compensate Step 2
                updateState(orderId, "COMPENSATING", "REFUNDING_PAYMENT");
                paymentService.refund(orderId, amount);

                // Compensate Step 1
                updateState(orderId, "COMPENSATING", "RELEASING_INVENTORY");
                inventoryService.release(itemId, quantity);
                throw e;
            }

            updateState(orderId, "COMPLETED", "DONE");
            return orderId;

        } catch (Exception e) {
            updateState(orderId, "FAILED", "SAGA_ABORTED");
            throw new RuntimeException("Saga aborted: " + e.getMessage(), e);
        }
    }

    private void updateState(UUID orderId, String status, String step) {
        int rows = jdbcTemplate.update(
            "UPDATE saga_state SET status = ?, current_step = ?, updated_at = NOW() WHERE order_id = ?", 
            status, step, orderId
        );
        if (rows == 0) {
            jdbcTemplate.update(
                "INSERT INTO saga_state (order_id, status, current_step) VALUES (?, ?, ?)", 
                orderId, status, step
            );
        }
    }
}
