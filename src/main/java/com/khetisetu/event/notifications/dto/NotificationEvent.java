// src/main/java/com/khetisetu/event/notifications/dto/NotificationEvent.java
package com.khetisetu.event.notifications.dto;

import com.khetisetu.event.notifications.model.EmailSenderConfig;

import java.util.Map;

public record NotificationEvent(
        String type,           // EMAIL, PUSH, SMS
        String recipient,      // email (for EMAIL/SMS), userId (for PUSH)
        String templateName,
        Map<String, String> params,
        String language,        // MUST be sent from producer
        String tenantId,        // which product this is sent on behalf of; absent => default tenant

        // IGNORED — send tenantId instead; sender identity comes from the tenant
        // registry. Producers used to embed the whole EmailSenderConfig here, SMTP
        // username and password included: credentials that were never used to send
        // anything (Brevo/SES hold their own) but were readable by anyone with topic
        // access and persisted on the broker's disk. Kept on the record only so
        // messages from not-yet-updated producers still deserialize.
        EmailSenderConfig senderConfig
) {}