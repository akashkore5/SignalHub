package com.khetisetu.event.notifications.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserTokenServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private UserTokenService userTokenService;

    @Test
    void getFcmTokens_ShouldReturnTokens_FromPushSubscriptionsList() {
        Map<String, Object> sub1 = new HashMap<>();
        sub1.put("token", "token_laptop");
        sub1.put("deviceId", "dev_1");

        Map<String, Object> sub2 = new HashMap<>();
        sub2.put("token", "token_mobile");
        sub2.put("deviceId", "dev_2");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscriptions", List.of(sub1, sub2));

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        List<String> tokens = userTokenService.getFcmTokens("user_123");

        assertEquals(2, tokens.size());
        assertTrue(tokens.contains("token_laptop"));
        assertTrue(tokens.contains("token_mobile"));
    }

    @Test
    void getFcmTokens_ShouldFallbackToLegacySingleSubscription() {
        Map<String, Object> pushSubscription = new HashMap<>();
        pushSubscription.put("token", "legacy_token");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscription", pushSubscription);

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        List<String> tokens = userTokenService.getFcmTokens("user_123");

        assertEquals(1, tokens.size());
        assertEquals("legacy_token", tokens.get(0));
    }

    @Test
    void getFcmTokens_ShouldDeduplicateTokens() {
        // Same token in both legacy and list
        Map<String, Object> sub = new HashMap<>();
        sub.put("token", "same_token");

        Map<String, Object> legacy = new HashMap<>();
        legacy.put("token", "same_token");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscriptions", List.of(sub));
        userMap.put("pushSubscription", legacy);

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        List<String> tokens = userTokenService.getFcmTokens("user_123");

        assertEquals(1, tokens.size());
    }

    @Test
    void getFcmTokens_ShouldReturnEmpty_WhenUserNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(null);

        List<String> tokens = userTokenService.getFcmTokens("user_123");

        assertTrue(tokens.isEmpty());
    }

    @Test
    void getFcmTokens_ShouldReturnEmpty_WhenNoSubscriptions() {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("pushSubscription", new HashMap<>());

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("users"))).thenReturn(userMap);

        List<String> tokens = userTokenService.getFcmTokens("user_123");

        assertTrue(tokens.isEmpty());
    }
}
