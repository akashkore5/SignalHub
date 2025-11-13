// src/main/java/com/khetisetu/app/notifications/orchestrator/dto/NotificationRequestEvent.java
package com.khetisetu.event.notifications.dto;


import com.khetisetu.event.notifications.model.EmailSenderConfig;
import lombok.Builder;

import java.util.Map;

@Builder
public record NotificationRequestEvent(
        String eventId,                    // UUID for idempotency
        String userId,
        String recipient,
        String type,
        String templateName,
        Map<String, String> params,
        String language,
        EmailSenderConfig senderConfig,
        String triggerId,                  // optional: for rule-based
        Map<String, Object> metadata
) {}