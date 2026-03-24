package com.distributedcorrectness.lock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class DistributedLockTest {

    @Container
    @SuppressWarnings("resource")
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
    private DistributedLockService lockService;

    @Autowired
    private SharedResourceService resourceService;

    @Test
    void testFencingTokenPreventsStaleWrites() {
        String resourceId = "config-file-123";

        // Worker 1 acquires lock and gets token
        Long tokenWorker1 = lockService.acquireLockAndToken(resourceId);
        assertEquals(1L, tokenWorker1);

        // --- Simulated GC Pause / Network Partition for Worker 1 ---
        // During the pause, the underlying lock system (e.g. Redis TTL or Zookeeper session) expires.
        // Worker 2 comes along, sees no active lock, and successfully acquires it.
        Long tokenWorker2 = lockService.acquireLockAndToken(resourceId);
        assertEquals(2L, tokenWorker2);

        // Worker 2 does its quick work and updates the storage
        resourceService.updateResource(resourceId, "worker_2_data", tokenWorker2);

        // --- Worker 1 wakes up from GC pause ---
        // Worker 1 believes it still holds the lock and attempts to write to storage.
        // Without fencing tokens, Worker 1 would overwrite Worker 2's newer data!
        
        Exception e = assertThrows(StaleTokenException.class, () -> {
            resourceService.updateResource(resourceId, "worker_1_data_STALE", tokenWorker1);
        });

        assertTrue(e.getMessage().contains("Fencing token rejected"));
        assertTrue(e.getMessage().contains("Provided token 1"));
    }
}
