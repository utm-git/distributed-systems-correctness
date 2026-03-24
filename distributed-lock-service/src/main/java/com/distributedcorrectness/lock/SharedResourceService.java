package com.distributedcorrectness.lock;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SharedResourceService {

    private final JdbcTemplate jdbcTemplate;

    public SharedResourceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Updates the shared resource, but strictly enforces fencing tokens to prevent split-brain overrides.
     */
    public void updateResource(String resourceId, String newValue, long fencingToken) {
        // Conditional update: only succeed if the incoming fencing token is strictly greater 
        // than the last fencing token that updated this resource.
        int rows = jdbcTemplate.update(
            "UPDATE shared_resource SET value = ?, last_fencing_token = ? WHERE resource_id = ? AND last_fencing_token < ?",
            newValue, fencingToken, resourceId, fencingToken
        );

        if (rows == 0) {
            // The update failed due to the fencing token condition. Fetch current for informative logging.
            Long currentFencingToken = jdbcTemplate.queryForObject(
                "SELECT last_fencing_token FROM shared_resource WHERE resource_id = ?", 
                Long.class, resourceId
            );
            
            throw new StaleTokenException(
                String.format("Fencing token rejected! Provided token %d is older/equal to current storage token %d. " +
                              "A split-brain/GC pause scenario was successfully prevented.", 
                              fencingToken, currentFencingToken)
            );
        }
    }
}

class StaleTokenException extends RuntimeException {
    public StaleTokenException(String message) {
        super(message);
    }
}
