package com.khetisetu.event.notifications.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Every Redis key this service writes is namespaced with this.
     *
     * <p>The shared Redis restricts each service to its own keyspace by ACL —
     * {@code signalhub} is granted {@code ~signalhub:*} and nothing else. The
     * app's keys ({@code idempotency:notif:*}, {@code rate:notif:*},
     * {@code conv:*}, {@code llm:key:exhausted:*}, …) carried no such prefix,
     * so every command was denied. Both call sites fail open — "Redis
     * unavailable, proceeding anyway" — so idempotency and rate limiting were
     * silently disabled in production rather than erroring.
     *
     * <p>Applied in the serializer rather than at each call site on purpose: a
     * key added later is namespaced automatically instead of quietly
     * reintroducing the same failure.
     */
    @Value("${spring.data.redis.key-prefix:signalhub:}")
    private String keyPrefix;

    /** Prepends {@link #keyPrefix} on write and strips it on read. */
    private RedisSerializer<String> prefixedKeySerializer() {
        final StringRedisSerializer delegate = new StringRedisSerializer();
        final String prefix = keyPrefix;
        return new RedisSerializer<String>() {
            @Override
            public byte[] serialize(String key) {
                return delegate.serialize(key == null ? null : prefix + key);
            }

            @Override
            public String deserialize(byte[] bytes) {
                String raw = delegate.deserialize(bytes);
                return (raw != null && raw.startsWith(prefix)) ? raw.substring(prefix.length()) : raw;
            }
        };
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // JSON Serializer with injected ObjectMapper
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(prefixedKeySerializer());
        template.setValueSerializer(serializer);
        // Hash FIELD names live inside a key, not in the keyspace, so the ACL
        // pattern does not apply to them and they must not be prefixed.
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper objectMapper) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(prefixedKeySerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // Cache notification templates or other metadata
        cacheConfigs.put("templates", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("senderConfigs", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
