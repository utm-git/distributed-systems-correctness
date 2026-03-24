package com.distributedcorrectness.saga;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShippingService {
    private final JdbcTemplate jdbcTemplate;

    public ShippingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ship(UUID orderId, String address, boolean fail) {
        if (fail) throw new RuntimeException("Simulated shipping failure");
        
        jdbcTemplate.update("INSERT INTO shipments (order_id, status) VALUES (?, ?)", 
                orderId, "SHIPPED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelShipment(UUID orderId) {
        jdbcTemplate.update("UPDATE shipments SET status = 'CANCELLED' WHERE order_id = ?", orderId);
    }
}
