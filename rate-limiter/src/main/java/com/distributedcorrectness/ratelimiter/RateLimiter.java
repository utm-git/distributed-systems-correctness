package com.distributedcorrectness.ratelimiter;

public interface RateLimiter {
    boolean allowRequest(String userId);
}
