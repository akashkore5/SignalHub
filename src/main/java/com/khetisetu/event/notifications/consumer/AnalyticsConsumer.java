package com.khetisetu.event.notifications.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationAnalyticsEvent;
import com.khetisetu.event.notifications.dto.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

import static com.khetisetu.event.notifications.constants.Constants.*;

/**
 * Kafka consumer component responsible for processing analytics events from the 'user-activity-analytics' topic.
 * It deserializes incoming events into specific DTOs (NotificationAnalyticsEvent or UserActivityEvent) based on their structure
 * and logs relevant information. Handles unknown or malformed events by logging warnings.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsConsumer {

    private final ObjectMapper objectMapper;

    /**
     * Kafka listener method that consumes records from the 'user-activity-analytics' topic.
     * Processes the record value by determining its type (NotificationAnalyticsEvent or UserActivityEvent)
     * and logging the event details. Handles exceptions and logs warnings for unknown event shapes.
     *
     * @param record the Kafka ConsumerRecord containing the event data
     */
    @KafkaListener(topics = "user-activity-analytics",
            groupId = "analytics-group",
            containerFactory = "analyticsFactory")
    public void listen(ConsumerRecord<String, Object> record) {
        try {
            Object value = record.value();
            log.info("Analytics event received: {}", value);

            // The JsonDeserializer produces a LinkedHashMap — route by key presence
            if (value instanceof Map<?, ?> map) {
                if (map.containsKey(EVENT_ID) && map.containsKey(STATUS)) {
                    var e = objectMapper.convertValue(map, NotificationAnalyticsEvent.class);
                    log(e);
                    return;
                }

                if (map.containsKey(USER_ID) && map.containsKey(ACTION)) {
                    UserActivityEvent e = objectMapper.convertValue(map, UserActivityEvent.class);
                    log(e);
                    return;
                }

                log.warn("Unknown analytics event shape: {}", map.keySet());
                return;
            }

            log.warn("Unexpected analytics value type: {}", value != null
                    ? value.getClass().getName()
                    : "null");

        } catch (Exception e) {
            log.error("Failed to process analytics event {} , {}", e.getMessage(),
                    Arrays.toString(e.getStackTrace()));
        }
    }

    /**
     * Logs details of a NotificationAnalyticsEvent.
     *
     * @param e the NotificationAnalyticsEvent to log
     */
    private void log(NotificationAnalyticsEvent e) {
        log.info("Notification {}: {} → {}", e.eventId(), e.type(), e.status());
    }

    /**
     * Logs details of a UserActivityEvent.
     *
     * @param e the UserActivityEvent to log
     */
    private void log(UserActivityEvent e) {
        log.info("User {}: {} → {}", e.userId(), e.type(), e.action());
    }
}