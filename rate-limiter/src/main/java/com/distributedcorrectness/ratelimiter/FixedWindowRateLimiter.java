package com.distributedcorrectness.ratelimiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class FixedWindowRateLimiter implements RateLimiter {
    private final StringRedisTemplate redisTemplate;
    
    // We'll hardcode 10 requests per second for simplicity in testing
    private final int limitPerWindow = 10;
    private final int windowSizeInSeconds = 1;

    public FixedWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allowRequest(String userId) {
        long currentWindow = Instant.now().getEpochSecond() / windowSizeInSeconds;
        String key = "fixed_window:" + userId + ":" + currentWindow;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, windowSizeInSeconds * 2, TimeUnit.SECONDS);
        }

        return count != null && count <= limitPerWindow;
    }
}
