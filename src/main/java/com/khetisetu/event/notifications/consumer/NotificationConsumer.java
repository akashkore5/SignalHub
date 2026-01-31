// src/main/java/com/khetisetu/event/notifications/consumer/NotificationConsumer.java
package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.NotificationProcessingService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

// src/main/java/com/khetisetu/event/notifications/consumer/NotificationConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationProcessingService processingService;
    private final Tracer tracer;

    @KafkaListener(topics = "notifications", groupId = "notification-event-group", containerFactory = "directFactory")
    public void listenDirect(NotificationEvent event, Acknowledgment ack) throws Exception {
        log.info("RECEIVED NotificationEvent from Kafka topic 'notifications': {}", event);
        Span span = tracer.spanBuilder("process.direct.notification")
                .setAttribute("type", event.type())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            processingService.process(event);
            ack.acknowledge();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            log.error("Direct processing failed", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    @KafkaListener(topics = "notification-requests", groupId = "delivery-group", containerFactory = "ruleFactory")
    public void listenRuleBased(NotificationRequestEvent event, Acknowledgment ack) throws Exception {
        Span span = tracer.spanBuilder("process.rule.notification")
                .setAttribute("event.id", event.eventId())
                .setAttribute("type", event.type())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            processingService.process(event);
            ack.acknowledge();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            log.error("Rule processing failed", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }
}