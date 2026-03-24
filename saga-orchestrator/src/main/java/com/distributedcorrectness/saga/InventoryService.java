package com.distributedcorrectness.saga;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final JdbcTemplate jdbcTemplate;

    public InventoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(String itemId, int quantity, boolean fail) {
        if (fail) throw new RuntimeException("Simulated inventory reservation failure");
        
        int rows = jdbcTemplate.update(
            "UPDATE inventory SET quantity = quantity - ? WHERE item_id = ? AND quantity >= ?", 
            quantity, itemId, quantity
        );
        if (rows == 0) {
            throw new RuntimeException("Insufficient inventory for item: " + itemId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String itemId, int quantity) {
        jdbcTemplate.update("UPDATE inventory SET quantity = quantity + ? WHERE item_id = ?", quantity, itemId);
    }
}
