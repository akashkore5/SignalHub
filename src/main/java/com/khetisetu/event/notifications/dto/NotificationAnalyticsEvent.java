// src/main/java/com/khetisetu/event/notifications/dto/NotificationAnalyticsEvent.java
package com.khetisetu.event.notifications.dto;

import lombok.Builder;
import java.time.Instant;

@Builder
public record NotificationAnalyticsEvent(
        String eventId,
        String userId,
        String type,
        String status,
        String error,
        Instant sentAt
) {}