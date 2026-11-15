// src/main/java/com/khetisetu/app/notifications/orchestrator/dto/NotificationRequestEvent.java
package com.khetisetu.event.notifications.dto;

import com.khetisetu.event.notifications.model.EmailSenderConfig;
import lombok.Builder;

import java.util.Map;

@Builder
public record NotificationRequestEvent(
                String eventId, // UUID for idempotency
                String userId,
                String recipient,
                String type,
                String templateName,
                Map<String, String> params,
                String language,
                String tenantId, // which product this is sent on behalf of; absent => default tenant

                // IGNORED — sender identity comes from the tenant registry.
                // Kept so older messages still deserialize.
                EmailSenderConfig senderConfig,
                String triggerId, // optional: for rule-based
                Map<String, Object> metadata,
                boolean sendPush,
                boolean sendEmail) {
}