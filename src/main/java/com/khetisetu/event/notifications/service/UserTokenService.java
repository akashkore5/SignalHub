package com.khetisetu.event.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserTokenService {

    private final MongoTemplate mongoTemplate;

    public String getFcmToken(String userId) {
        try {
            Query query = new Query(Criteria.where("_id").is(userId));
            query.fields().include("pushSubscription");
            Map<String, Object> user = mongoTemplate.findOne(query, Map.class, "users");

            if (user != null && user.containsKey("pushSubscription")) {
                Object sub = user.get("pushSubscription");
                if (sub instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> pushSubscriptionMap = (Map<String, Object>) sub;

                    // Check for 'token' key (set by newer frontend versions)
                    if (pushSubscriptionMap.containsKey("token")) {
                        return (String) pushSubscriptionMap.get("token");
                    }

                    // Fallback: extract FCM token from endpoint URL
                    // Format: https://fcm.googleapis.com/fcm/send/<FCM_TOKEN>
                    if (pushSubscriptionMap.containsKey("endpoint")) {
                        String endpoint = (String) pushSubscriptionMap.get("endpoint");
                        if (endpoint != null && endpoint.contains("fcm.googleapis.com/fcm/send/")) {
                            String token = endpoint.substring(endpoint.lastIndexOf("/") + 1);
                            log.info("Extracted FCM token from endpoint URL for user {}", userId);
                            return token;
                        }
                    }

                    // Fallback to checking keys.fcm (example if nested differently)
                    if (pushSubscriptionMap.containsKey("keys")) {
                        Object keys = pushSubscriptionMap.get("keys");
                        if (keys instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> keysMap = (Map<String, Object>) keys;
                            if (keysMap.containsKey("fcm")) {
                                return (String) keysMap.get("fcm");
                            }
                        }
                    }
                }
            } else if (user != null) {
                log.debug("User {} found but missing pushSubscription fields", userId);
            }
        } catch (Exception e) {
            log.error("Failed to fetch token for user {}: {}", userId, e.getMessage());
        }
        return null;
    }
}
