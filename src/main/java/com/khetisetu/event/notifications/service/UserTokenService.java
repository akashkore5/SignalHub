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
                int idx = 0;
                for (Object item : (List<?>) subsList) {
                    String token = extractTokenFromSubscription(item);
                    if (token != null) {
                        tokens.add(token);
                        log.info("[FCM DEBUG] User {} pushSubscriptions[{}] token: {}... (len={})",
                                userId, idx, token.substring(0, Math.min(20, token.length())), token.length());
                    }
                    idx++;
                }
            }

            // 2. Fallback: read from legacy single pushSubscription
            Object sub = user.get("pushSubscription");
            String legacyToken = extractTokenFromSubscription(sub);
            if (legacyToken != null) {
                tokens.add(legacyToken);
                log.info("[FCM DEBUG] User {} legacy pushSubscription token: {}... (len={})",
                        userId, legacyToken.substring(0, Math.min(20, legacyToken.length())), legacyToken.length());
            }

            if (tokens.isEmpty()) {
                log.warn("[FCM DEBUG] User {} has NO FCM tokens in DB at all", userId);
            } else {
                log.info("[FCM DEBUG] User {} total unique tokens to send: {}", userId, tokens.size());
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
     * Removes a specific stale token from both pushSubscriptions list
     * and the legacy pushSubscription field.
     * Handles all 3 token storage formats:
     * 1. token field directly
     * 2. embedded in endpoint URL (fcm.googleapis.com/fcm/send/{token})
     * 3. keys.fcm field
     */
    public void invalidateToken(String userId, String staleToken) {
        try {
            String tokenPrefix = staleToken.substring(0, Math.min(10, staleToken.length()));
            long totalRemoved = 0;

            Query userQuery = new Query(Criteria.where("_id").is(userId));

            // Strategy 1: pull from pushSubscriptions[] where token field matches
            var result1 = mongoTemplate.updateFirst(userQuery,
                    new Update().pull("pushSubscriptions",
                            new org.bson.Document("token", staleToken)),
                    "users");
            totalRemoved += result1.getModifiedCount();

            // Strategy 2: pull from pushSubscriptions[] where endpoint URL contains the
            // token
            String endpointSuffix = "fcm.googleapis.com/fcm/send/" + staleToken;
            var result2 = mongoTemplate.updateFirst(userQuery,
                    new Update().pull("pushSubscriptions",
                            new org.bson.Document("endpoint",
                                    new org.bson.Document("$regex",
                                            ".*" + java.util.regex.Pattern.quote(staleToken) + "$"))),
                    "users");
            totalRemoved += result2.getModifiedCount();

            // Strategy 3: pull from pushSubscriptions[] where keys.fcm matches
            var result3 = mongoTemplate.updateFirst(userQuery,
                    new Update().pull("pushSubscriptions",
                            new org.bson.Document("keys.fcm", staleToken)),
                    "users");
            totalRemoved += result3.getModifiedCount();

            // Strategy 4: unset legacy pushSubscription if its token matches (any format)
            Criteria legacyCriteria = Criteria.where("_id").is(userId).orOperator(
                    Criteria.where("pushSubscription.token").is(staleToken),
                    Criteria.where("pushSubscription.endpoint")
                            .regex(".*" + java.util.regex.Pattern.quote(staleToken) + "$"),
                    Criteria.where("pushSubscription.keys.fcm").is(staleToken));
            var result4 = mongoTemplate.updateFirst(new Query(legacyCriteria),
                    new Update().unset("pushSubscription"), "users");
            totalRemoved += result4.getModifiedCount();

            if (totalRemoved > 0) {
                log.info("Successfully removed stale FCM token for user {} (token: {}..., {} document updates)",
                        userId, tokenPrefix, totalRemoved);
            } else {
                log.warn("Failed to remove stale FCM token for user {} (token: {}...) — token not found in any format. "
                        + "Manual DB cleanup may be needed.", userId, tokenPrefix);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate token for user {}: {}", userId, e.getMessage(), e);
        }
    }
}
