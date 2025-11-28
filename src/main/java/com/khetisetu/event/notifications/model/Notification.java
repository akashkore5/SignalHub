// src/main/java/com/khetisetu/event/notifications/model/Notification.java
package com.khetisetu.event.notifications.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Data
@Document(collection = "notifications")
public class Notification extends BaseModel {
    private String eventId;           // <-- NEW: unique event ID from Kafka
    private String userId;            // <-- NEW: userId (for PUSH, analytics)
    private String type;              // EMAIL, SMS, PUSH, WHATSAPP
    private String recipient;         // email / phone / userId
    private String subject;
    private String content;
    private String status;            // PENDING, SENT, FAILED, SKIPPED
    private String errorMessage;
    private String templateName;
    private int retryCount;
    private boolean isRead;
    private Map<String, Object> metadata; // <-- NEW: store triggerId, ruleId, etc.
}