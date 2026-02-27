package com.khetisetu.event.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTokenService {

    private final MongoTemplate mongoTemplate;

    /**
     * Returns ALL FCM tokens for a user across all their devices.
     * Reads from both the new `pushSubscriptions` list and the legacy
     * `pushSubscription` field.
     */
    public List<String> getFcmTokens(String userId) {
        Set<String> tokens = new LinkedHashSet<>(); // Deduplicate

        try {
            Query query = new Query(Criteria.where("_id").is(userId));
            query.fields().include("pushSubscription").include("pushSubscriptions");
            Map<String, Object> user = mongoTemplate.findOne(query, Map.class, "users");

            if (user == null)
                return List.of();

            // 1. Read from new pushSubscriptions list
            Object subsList = user.get("pushSubscriptions");
            if (subsList instanceof List) {
                for (Object item : (List<?>) subsList) {
                    String token = extractTokenFromSubscription(item);
                    if (token != null)
                        tokens.add(token);
                }
            }

            // 2. Fallback: read from legacy single pushSubscription
            Object sub = user.get("pushSubscription");
            String legacyToken = extractTokenFromSubscription(sub);
            if (legacyToken != null)
                tokens.add(legacyToken);

            if (tokens.isEmpty()) {
                log.debug("User {} has no FCM tokens", userId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch tokens for user {}: {}", userId, e.getMessage());
        }

        return new ArrayList<>(tokens);
    }

    @SuppressWarnings("unchecked")
    private String extractTokenFromSubscription(Object sub) {
        if (!(sub instanceof Map))
            return null;
        Map<String, Object> subMap = (Map<String, Object>) sub;

        // Check for 'token' key (set by newer frontend versions)
        if (subMap.containsKey("token")) {
            return (String) subMap.get("token");
        }

        // Fallback: extract from endpoint URL
        if (subMap.containsKey("endpoint")) {
            String endpoint = (String) subMap.get("endpoint");
            if (endpoint != null && endpoint.contains("fcm.googleapis.com/fcm/send/")) {
                return endpoint.substring(endpoint.lastIndexOf("/") + 1);
            }
        }

        // Fallback: keys.fcm
        if (subMap.containsKey("keys")) {
            Object keys = subMap.get("keys");
            if (keys instanceof Map) {
                Map<String, Object> keysMap = (Map<String, Object>) keys;
                if (keysMap.containsKey("fcm")) {
                    return (String) keysMap.get("fcm");
                }
            }
        }

        return null;
    }

    /**
     * Atomically removes a specific stale token from both pushSubscriptions list
     * and the legacy pushSubscription field. Single DB call, no read required.
     */
    public void invalidateToken(String userId, String staleToken) {
        try {
            // Atomic: pull from array + unset legacy field if it matches
            Query query = new Query(Criteria.where("_id").is(userId));
            Update update = new Update()
                    .pull("pushSubscriptions", new org.bson.Document("token", staleToken));

            // First update: remove from array
            mongoTemplate.updateFirst(query, update, "users");

            // Second atomic update: unset legacy field only if its token matches
            Query legacyQuery = new Query(
                    Criteria.where("_id").is(userId)
                            .and("pushSubscription.token").is(staleToken));
            Update legacyUpdate = new Update().unset("pushSubscription");
            mongoTemplate.updateFirst(legacyQuery, legacyUpdate, "users");

            log.info("Invalidated stale FCM token for user {} (token: {}...)", userId,
                    staleToken.substring(0, Math.min(10, staleToken.length())));
        } catch (Exception e) {
            log.error("Failed to invalidate token for user {}: {}", userId, e.getMessage());
        }
    }
}
