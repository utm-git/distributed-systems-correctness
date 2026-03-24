package com.distributedcorrectness.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class RateLimiterTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @Autowired
    private FixedWindowRateLimiter fixedWindow;

    @Autowired
    private SlidingWindowRateLimiter slidingWindow;

    @Autowired
    private TokenBucketRateLimiter tokenBucket;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanup() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    // ---- Fixed Window Tests ----

    @Test
    void fixedWindow_allowsUpToLimit() {
        for (int i = 0; i < 10; i++) {
            assert fixedWindow.allowRequest("user_fixed") : "Request " + i + " should be allowed";
        }
    }

    @Test
    void fixedWindow_blocksOverLimit() {
        for (int i = 0; i < 10; i++) {
            fixedWindow.allowRequest("user_fixed_block");
        }
        boolean result = fixedWindow.allowRequest("user_fixed_block");
        assertEquals(false, result, "11th request should be blocked");
    }

    // ---- Sliding Window Tests ----

    @Test
    void slidingWindow_allowsUpToLimit() {
        for (int i = 0; i < 10; i++) {
            assert slidingWindow.allowRequest("user_sliding") : "Request " + i + " should be allowed";
        }
    }

    @Test
    void slidingWindow_blocksOverLimit() {
        for (int i = 0; i < 10; i++) {
            slidingWindow.allowRequest("user_sliding_block");
        }
        boolean result = slidingWindow.allowRequest("user_sliding_block");
        assertEquals(false, result, "11th request should be blocked");
    }

    // ---- Token Bucket Tests ----

    @Test
    void tokenBucket_allowsUpToCapacity() {
        for (int i = 0; i < 10; i++) {
            assert tokenBucket.allowRequest("user_bucket") : "Request " + i + " should be allowed";
        }
    }

    @Test
    void tokenBucket_blocksAfterCapacity() {
        for (int i = 0; i < 10; i++) {
            tokenBucket.allowRequest("user_bucket_block");
        }
        boolean result = tokenBucket.allowRequest("user_bucket_block");
        assertEquals(false, result, "11th request should be blocked since bucket is empty");
    }

    // ---- Concurrent burst test across all three ----

    @Test
    void concurrentBurst_fixedWindow_enforcesLimit() throws InterruptedException {
        int totalRequests = 50;
        AtomicInteger allowed = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    if (fixedWindow.allowRequest("burst_user_fixed")) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        startGate.countDown();
        latch.await();
        executor.shutdown();

        // Exactly 10 of the 50 concurrent requests should have been allowed
        assertEquals(10, allowed.get(), "Fixed window should allow exactly 10 out of 50 burst requests");
    }
}
