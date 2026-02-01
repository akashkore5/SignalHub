package com.khetisetu.event.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;
    private static final DateTimeFormatter DATES = DateTimeFormatter.ISO_DATE;
    private static final String KEY_PREFIX = "global:rate:";

    /**
     * Try to acquire a permit for the given type.
     * 
     * @param type  The resource type (e.g. "EMAIL")
     * @param limit The daily limit
     * @return true if acquired, false if limit exceeded
     */
    public boolean tryAcquire(String type, int limit) {
        String key = getKey(type);
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current == null)
                return false;

            if (current == 1) {
                // Set expiry for 24 hours (plus buffer) on first increment
                redisTemplate.expire(key, 25, TimeUnit.HOURS);
            }

            if (current > limit) {
                // Optionally decrement if we don't want to count rejected requests,
                // but usually rate limits count the attempts or just stop incrementing.
                // Here we just return false.
                // To keep the counter accurate to "attempts" vs "allowed", we leave it.
                // If strictly "allowed", we should decrement, but race/atomic issues valid.
                // Simple counter is fine.
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to access Redis for rate limiting", e);
            // Open fallback: allow if redis fails? Or deny?
            // Safer to allow to avoid denial of service on cache failure,
            // but risky for costs. Let's allow but log.
            return true;
        }
    }

    public long getUsage(String type) {
        try {
            String val = redisTemplate.opsForValue().get(getKey(type));
            return val != null ? Long.parseLong(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String getKey(String type) {
        return KEY_PREFIX + type + ":" + LocalDate.now().format(DATES);
    }
}
