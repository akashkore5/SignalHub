package com.khetisetu.event.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GlobalRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private static final DateTimeFormatter DATES = DateTimeFormatter.ISO_DATE;
    private static final String KEY_PREFIX = "global:rate:";

    // Lua script for atomic increment and expire
    private static final String RATE_LIMIT_SCRIPT = "local current = redis.call('incr', KEYS[1]) " +
            "if current == 1 then " +
            "   redis.call('expire', KEYS[1], ARGV[1]) " +
            "end " +
            "return current";

    public boolean tryAcquire(String type, int limit) {
        String key = getKey(type);
        try {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptText(RATE_LIMIT_SCRIPT);
            redisScript.setResultType(Long.class);

            // Expire in 25 hours (seconds)
            String expireSeconds = String.valueOf(25 * 3600);

            Long current = redisTemplate.execute(redisScript, Collections.singletonList(key), expireSeconds);

            if (current == null)
                return false;

            if (current > limit) {
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to access Redis for rate limiting", e);
            // Open fallback: allow if redis fails to avoid outage
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
