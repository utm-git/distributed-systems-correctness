package com.distributedcorrectness.lock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributedLockService {

    private final JdbcTemplate jdbcTemplate;

    public DistributedLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Obtains a fencing token for a specific resource. 
     * Simulates the "Lock Grant" phase in a distributed system. 
     * We use Postgres transaction-level advisory locks to serialize the token generation safely without table bloat.
     */
    @Transactional
    public Long acquireLockAndToken(String resourceId) {
        // Hash the resourceId to a 64-bit integer for the advisory lock
        long lockId = resourceId.hashCode();
        
        // Acquire an exclusive transaction-level lock on this resource's hash
        Boolean locked = jdbcTemplate.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, lockId);
        
        if (Boolean.FALSE.equals(locked)) {
            throw new RuntimeException("Could not acquire lock for resource: " + resourceId);
        }

        // Lock is acquired. Safely increment and fetch the fencing token.
        return jdbcTemplate.queryForObject(
            "UPDATE fencing_tokens SET current_token = current_token + 1 WHERE resource_id = ? RETURNING current_token",
            Long.class, resourceId
        );
    }
}
