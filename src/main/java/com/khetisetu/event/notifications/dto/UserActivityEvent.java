package com.khetisetu.event.notifications.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.Map;

// New Topic: user-activity-analytics
@Builder
public record UserActivityEvent(
        String eventId,
        String userId,
        String type,           // NOTIFICATION_SENT, LOGIN, JOB_APPLIED, etc.
        String action,
        Map<String, Object> metadata,
        Instant timestamp
) {}