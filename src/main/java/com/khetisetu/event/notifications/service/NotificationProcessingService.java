package com.khetisetu.event.notifications.service;


import com.khetisetu.event.logs.service.LogService;
import com.khetisetu.event.notifications.dto.NotificationAnalyticsEvent;
import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.model.logs.Actor;
import com.khetisetu.event.notifications.model.logs.Entity;
import com.khetisetu.event.notifications.provider.NotificationProvider;
import com.khetisetu.event.notifications.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.khetisetu.event.constants.EntityConstants.*;
import static com.khetisetu.event.constants.LogLevel.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;
    private final Map<String, NotificationProvider> providers;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;

    @Autowired
    LogService logService;

    private static final String IDEMPOTENCY_KEY = "idempotency:notif:%s";

    // === PROCESS DIRECT EVENT ===
    @Async
    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void process(NotificationEvent event) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String userId = event.recipient();

        MDC.put("traceId", eventId);
        MDC.put("eventId", eventId);
        MDC.put("userId", userId);
        MDC.put("type", event.type());

        log.info("Processing direct notification");

        if (isAlreadyProcessed(eventId)) {
            log.info("Duplicate direct event");
            MDC.clear();
            return;
        }

        NotificationRequestEvent req = toRequestEvent(event, eventId, userId);
        processRequest(req);
        markAsProcessed(eventId);
        MDC.clear();
    }

    // === PROCESS RULE-BASED EVENT ===
    @Async
    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void process(NotificationRequestEvent event) throws Exception {
        String traceId = event.triggerId() != null ? event.triggerId() : event.eventId();

        MDC.put("traceId", traceId);
        MDC.put("eventId", event.eventId());
        MDC.put("userId", event.userId());
        MDC.put("type", event.type());

        log.info("Processing rule-based notification");

        if (isAlreadyProcessed(event.eventId())) {
            log.info("Duplicate rule event");
            MDC.clear();
            return;
        }

        processRequest(event);
        markAsProcessed(event.eventId());
        MDC.clear();
    }

    private void processRequest(NotificationRequestEvent event) throws Exception {
        boolean pushAttempted = false;
        boolean emailAttempted = false;
        Exception lastException = null;

        if (event.sendPush()) {
            pushAttempted = true;
            try {
                sendToProvider(event, "PUSH");
            } catch (Exception e) {
                log.error("Failed to send PUSH for event {}: {} {}", event.eventId(), e.getMessage(), Arrays.toString(e.getStackTrace()));
                lastException = e;
            }
        }

        if (event.sendEmail()) {
            emailAttempted = true;
            try {
                sendToProvider(event, "EMAIL");
            } catch (Exception e) {
                log.error("Failed to send EMAIL for event {}: {} , {}", event.eventId(), e.getMessage(), Arrays.toString(e.getStackTrace()));
                lastException = e;
            }
        }

        // Fallback or legacy behavior if neither flag is explicit, rely on 'type' if
        // present
        if (!pushAttempted && !emailAttempted && event.type() != null) {
            sendToProvider(event, event.type());
        } else if (lastException != null) {
            // If both were attempted and at least one failed, we might want to propagate if
            // we want Kafka retry.
            // But if one SUCCEEDED, a retry would duplicate the success.
            // Idempotency handles duplicates, so re-throwing is generally safer.
            throw lastException;
        }
    }

    private void sendToProvider(NotificationRequestEvent event, String type) throws Exception {
        if (!canSend(event.recipient(), type)) {
            log.warn("Rate limit exceeded for {}", type);
            publishAnalytics(event, "RATE_LIMITED", null);
            return;
        }

        Notification notification = createNotification(event, type);
        notification = notificationRepository.save(notification);

        try {
            NotificationProvider provider = providers.get(type);
            if (provider == null) {
                log.warn("No provider found for type: {}", type);
                throw new IllegalStateException("No provider: " + type);
            }

            provider.send(event, notification);
            updateStatus(notification, "SENT", null);
            publishAnalytics(event, "SENT", null);
            meterRegistry.counter("notification.sent", "type", type).increment();
            Actor actor = new Actor(event.userId(), USER);
            Entity entity = new Entity(event.eventId(), "NOTIFICATION_EVENT");
            logService.storeLog(actor, event.type() + NOTIFICATION, entity, "Successfully publish event.", INFO);

        } catch (Exception e) {
            log.error("Send failed processing event {} for type {}", event.eventId(), type, e);
            Actor actor = new Actor(event.userId(), USER);
            Entity entity = new Entity(event.eventId(), "NOTIFICATION_EVENT");
            logService.storeLog(actor, event.type() + NOTIFICATION, entity, "Failed to publish event", ERROR);
            updateStatus(notification, "FAILED", e.getMessage());
            try {
                publishAnalytics(event, "FAILED", e.getMessage());
            } catch (Exception analyticsEx) {
                logService.storeLog(actor, "ANALYTICS_EVENT", entity, "Failed to publish Analytic event", ERROR);
                log.error("Failed to publish failure analytics for event {}", event.eventId(), analyticsEx);
            }
            meterRegistry.counter("notification.failed", "type", type).increment();
            throw e;
        }
    }

    private NotificationRequestEvent toRequestEvent(NotificationEvent event, String eventId, String userId) {
        return NotificationRequestEvent.builder()
                .eventId(eventId)
                .userId(userId)
                .recipient(event.recipient())
                .type(event.type())
                .templateName(event.templateName())
                .params(event.params())
                .language(event.language())
                .senderConfig(event.senderConfig())
                .triggerId(null)
                .metadata(Map.of("source", "direct"))
                .build();
    }

    private boolean isAlreadyProcessed(String eventId) {
        try {
            return redisTemplate.hasKey(String.format(IDEMPOTENCY_KEY, eventId));
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency check (eventId={}). Proceeding anyway.", eventId);
            return false; // Allow processing when Redis is down
        }
    }

    private void markAsProcessed(String eventId) {
        try {
            redisTemplate.opsForValue().set(String.format(IDEMPOTENCY_KEY, eventId), "1", 24, TimeUnit.HOURS);
        } catch (Exception e) {
            Actor actor = new Actor("", "SYSTEM");
            Entity entity = new Entity(eventId, "REDIS_IDEMPOTENCY");
            logService.storeLog(actor, "REDIS_UPDATE", entity, "Failed to update redis cache so skipping", ERROR);
            log.warn("Redis unavailable for marking processed (eventId={}). Skipping.", eventId);
        }
    }

    private boolean canSend(String recipient, String type) {
        try {
            String key = "rate:notif:" + recipient + ":" + type;
            String val = redisTemplate.opsForValue().get(key);
            long count = val == null ? 0 : Long.parseLong(val);
            if (count >= 5)
                return false;
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limiting ({}/{}). Allowing send.", recipient, type);
        }
        return true;
    }

    private Notification createNotification(NotificationRequestEvent event, String type) {
        Notification n = new Notification();
        n.setEventId(event.eventId());
        n.setUserId(event.userId());
        n.setType(type);
        n.setRecipient(event.recipient());
        n.setTemplateName(event.templateName());
        n.setStatus("PENDING");
        n.setRetryCount(0);
        n.setMetadata(event.metadata());
        n.setCreatedAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        return n;
    }

    private void updateStatus(Notification n, String status, String error) {
        n.setStatus(status);
        n.setErrorMessage(error);
        if ("FAILED".equals(status))
            n.setRetryCount(n.getRetryCount() + 1);
        n.setUpdatedAt(Instant.now());
        notificationRepository.save(n);
    }

    private void publishAnalytics(NotificationRequestEvent event, String status, String error) {
        var analytics = NotificationAnalyticsEvent.builder()
                .eventId(event.eventId())
                .userId(event.userId())
                .type(event.type())
                .status(status)
                .error(error)
                .sentAt(Instant.now())
                .build();
        kafkaTemplate.send("user-activity-analytics", analytics);
    }
}