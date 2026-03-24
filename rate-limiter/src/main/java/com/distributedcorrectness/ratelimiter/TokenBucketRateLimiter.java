package com.distributedcorrectness.ratelimiter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;

@Component
public class TokenBucketRateLimiter implements RateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    private final int bucketCapacity = 10;
    private final int refillRatePerSecond = 10;

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(
            "local capacity = tonumber(ARGV[1])\n" +
            "local refill_rate = tonumber(ARGV[2])\n" +
            "local requested = tonumber(ARGV[3])\n" +
            "local now = tonumber(ARGV[4])\n" +
            "local last_tokens = tonumber(redis.call('GET', KEYS[1]))\n" +
            "if last_tokens == nil then last_tokens = capacity end\n" +
            "local last_refreshed = tonumber(redis.call('GET', KEYS[2]))\n" +
            "if last_refreshed == nil then last_refreshed = now end\n" +
            "local delta = math.max(0, now - last_refreshed)\n" +
            "local filled_tokens = math.min(capacity, last_tokens + (delta * refill_rate))\n" +
            "local allowed = filled_tokens >= requested\n" +
            "local new_tokens = filled_tokens\n" +
            "if allowed then\n" +
            "    new_tokens = filled_tokens - requested\n" +
            "end\n" +
            "redis.call('SETEX', KEYS[1], 3600, new_tokens)\n" +
            "redis.call('SETEX', KEYS[2], 3600, now)\n" +
            "return allowed and 1 or 0",
            Long.class
        );
    }

    @Override
    public boolean allowRequest(String userId) {
        long now = Instant.now().getEpochSecond();
        String bucketKey = "token_bucket:tokens:" + userId;
        String timeKey = "token_bucket:time:" + userId;

        Long result = redisTemplate.execute(
            script,
            Arrays.asList(bucketKey, timeKey),
            String.valueOf(bucketCapacity),
            String.valueOf(refillRatePerSecond),
            "1",
            String.valueOf(now)
        );

        return result != null && result == 1L;
    }
}
