package com.distributedcorrectness.saga;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {
    private final JdbcTemplate jdbcTemplate;

    public PaymentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void charge(UUID orderId, BigDecimal amount, boolean fail) {
        if (fail) throw new RuntimeException("Simulated payment charge failure");
        
        jdbcTemplate.update("INSERT INTO payments (order_id, amount, status) VALUES (?, ?, ?)", 
                orderId, amount, "CHARGED");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refund(UUID orderId, BigDecimal amount) {
        jdbcTemplate.update("UPDATE payments SET status = 'REFUNDED' WHERE order_id = ?", orderId);
    }
}
