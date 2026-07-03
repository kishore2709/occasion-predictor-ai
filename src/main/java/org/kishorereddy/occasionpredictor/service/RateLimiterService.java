package org.kishorereddy.occasionpredictor.service;

import org.kishorereddy.occasionpredictor.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_KEY = "rate:";

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.rate-limit.requests-per-minute:20}")
    private int requestsPerMinute;

    public RateLimiterService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Fixed-window rate limiter keyed by {@code clientKey} (typically client IP).
     * Throws {@link RateLimitExceededException} when the limit is exceeded.
     * If Redis is unavailable the request is allowed through (fail-open).
     */
    public void checkOrThrow(String clientKey) {
        try {
            long window = System.currentTimeMillis() / 60_000;
            String key  = RATE_KEY + clientKey + ":" + window;

            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 60, TimeUnit.SECONDS);
            }
            if (count != null && count > requestsPerMinute) {
                log.warn("Rate limit exceeded for key={} count={}", clientKey, count);
                throw new RateLimitExceededException(
                        "Rate limit exceeded — max " + requestsPerMinute + " requests per minute");
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Rate limiter unavailable, allowing request (key={}): {}", clientKey, e.getMessage());
        }
    }
}
