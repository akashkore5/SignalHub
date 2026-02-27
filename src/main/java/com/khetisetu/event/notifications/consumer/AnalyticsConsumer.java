package com.khetisetu.event.notifications.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationAnalyticsEvent;
import com.khetisetu.event.notifications.dto.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user-activity-analytics", groupId = "analytics-group", containerFactory = "analyticsFactory")
    public void listen(ConsumerRecord<String, Object> record) {
        try {
            Object value = record.value();
            log.info("Analytics event received: {}", value);

            // The JsonDeserializer produces a LinkedHashMap — route by key presence
            if (value instanceof Map<?, ?> map) {
                if (map.containsKey("eventId") && map.containsKey("status")) {
                    NotificationAnalyticsEvent e = objectMapper.convertValue(map, NotificationAnalyticsEvent.class);
                    handleNotificationAnalytics(e);
                } else if (map.containsKey("userId") && map.containsKey("action")) {
                    UserActivityEvent e = objectMapper.convertValue(map, UserActivityEvent.class);
                    handleUserActivity(e);
                } else {
                    log.warn("Unknown analytics event shape: {}", map.keySet());
                }
            } else {
                log.warn("Unexpected analytics value type: {}", value != null ? value.getClass().getName() : "null");
            }
        } catch (Exception e) {
            log.error("Failed to process analytics event", e);
        }
    }

    private void handleNotificationAnalytics(NotificationAnalyticsEvent e) {
        log.info("Notification {}: {} → {}", e.eventId(), e.type(), e.status());
    }

    private void handleUserActivity(UserActivityEvent e) {
        log.info("User {}: {} → {}", e.userId(), e.type(), e.action());
    }
}