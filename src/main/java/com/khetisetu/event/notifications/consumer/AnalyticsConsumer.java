package com.khetisetu.event.notifications.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationAnalyticsEvent;
import com.khetisetu.event.notifications.dto.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

// src/main/java/com/khetisetu/event/notifications/consumer/AnalyticsConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "user-activity-analytics",
            groupId = "analytics-group",
            containerFactory = "analyticsFactory"
    )
    public void listen(Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            log.info("Analytics event: {}", json);

            // Route based on type
            if (event instanceof NotificationAnalyticsEvent e) {
                handleNotificationAnalytics(e);
            } else if (event instanceof UserActivityEvent e) {
                handleUserActivity(e);
            } else {
                log.warn("Unknown analytics event: {}", event.getClass());
            }
        } catch (Exception e) {
            log.error("Failed to process analytics event", e);
        }
    }

    private void handleNotificationAnalytics(NotificationAnalyticsEvent e) {
        log.info("Notification {}: {} → {}", e.eventId(), e.type(), e.status());
        // Save to ClickHouse / BigQuery / etc.
    }

    private void handleUserActivity(UserActivityEvent e) {
        log.info("User {}: {} → {}", e.userId(), e.type(), e.action());
        // Enrich with user data, save to analytics DB
    }
}