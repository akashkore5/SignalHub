package com.khetisetu.event.notifications.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationAnalyticsEvent;
import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.provider.NotificationProvider;
import com.khetisetu.event.notifications.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationProcessingService {

    private final NotificationRepository notificationRepository;
    private final Map<String, NotificationProvider> providers;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENCY_KEY = "idempotency:notif:%s";

    // === PROCESS DIRECT EVENT ===
    @Async
    @Retryable(maxAttempts = 4, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void process(NotificationEvent event) throws Exception {
        String eventId = UUID.randomUUID().toString();
        String userId = extractUserId(event);
        String traceId = eventId;

        MDC.put("traceId", traceId);
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
        if (!canSend(event.recipient(), event.type())) {
            log.warn("Rate limit exceeded");
            publishAnalytics(event, "RATE_LIMITED", null);
            return;
        }

        Notification notification = createNotification(event);
        notification = notificationRepository.save(notification);

        try {
            NotificationProvider provider = providers.get(event.type());
            if (provider == null)
                throw new IllegalStateException("No provider: " + event.type());

            provider.send(event, notification);
            updateStatus(notification, "SENT", null);
            publishAnalytics(event, "SENT", null);
            meterRegistry.counter("notification.sent", "type", event.type()).increment();

        } catch (Exception e) {
            log.error("Send failed processing event {}", event.eventId(), e);
            updateStatus(notification, "FAILED", e.getMessage());
            try {
                publishAnalytics(event, "FAILED", e.getMessage());
            } catch (Exception analyticsEx) {
                log.error("Failed to publish failure analytics for event {}", event.eventId(), analyticsEx);
            }
            meterRegistry.counter("notification.failed", "type", event.type()).increment();
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

    private String extractUserId(NotificationEvent event) {
        return switch (event.type()) {
            case "PUSH" -> event.recipient();
            case "EMAIL" -> event.recipient();
            // case "EMAIL" ->
            // userRepository.findByEmail(event.recipient()).map(User::getId).orElse("unknown");
            default -> "unknown";
        };
    }

    private boolean isAlreadyProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(String.format(IDEMPOTENCY_KEY, eventId)));
    }

    private void markAsProcessed(String eventId) {
        redisTemplate.opsForValue().set(String.format(IDEMPOTENCY_KEY, eventId), "1", 24, TimeUnit.HOURS);
    }

    private boolean canSend(String recipient, String type) {
        String key = "rate:notif:" + recipient + ":" + type;
        String val = redisTemplate.opsForValue().get(key);
        long count = val == null ? 0 : Long.parseLong(val);
        if (count >= 5)
            return false;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        return true;
    }

    private Notification createNotification(NotificationRequestEvent event) {
        Notification n = new Notification();
        n.setEventId(event.eventId());
        n.setUserId(event.userId());
        n.setType(event.type());
        n.setRecipient(event.recipient());
        n.setTemplateName(event.templateName());
        n.setStatus("PENDING");
        n.setRetryCount(0);
        n.setMetadata(event.metadata());
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