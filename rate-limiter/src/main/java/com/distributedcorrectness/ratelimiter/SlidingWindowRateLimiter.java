package com.distributedcorrectness.ratelimiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

@Component
public class SlidingWindowRateLimiter implements RateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    private final int limitPerWindow = 10;
    private final int windowSizeInMs = 1000;

    public SlidingWindowRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(
            "local current_time = tonumber(ARGV[1])\n" +
            "local window_start = current_time - tonumber(ARGV[2])\n" +
            "local max_requests = tonumber(ARGV[3])\n" +
            "local current_uuid = ARGV[4]\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, window_start)\n" +
            "local request_count = redis.call('ZCARD', KEYS[1])\n" +
            "if request_count < max_requests then\n" +
            "    redis.call('ZADD', KEYS[1], current_time, current_uuid)\n" +
            "    redis.call('EXPIRE', KEYS[1], math.ceil(tonumber(ARGV[2])/1000) * 2)\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end",
            Long.class
        );
    }

    @Override
    public boolean allowRequest(String userId) {
        long now = Instant.now().toEpochMilli();
        String uniqueId = now + "-" + UUID.randomUUID();
        String key = "sliding_window:" + userId;

        Long result = redisTemplate.execute(
            script,
            Collections.singletonList(key),
            String.valueOf(now),
            String.valueOf(windowSizeInMs),
            String.valueOf(limitPerWindow),
            uniqueId
        );

        return result != null && result == 1L;
    }
}
