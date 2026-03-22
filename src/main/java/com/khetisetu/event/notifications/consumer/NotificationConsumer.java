package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationEvent;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.NotificationProcessingService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka consumer component for processing notification events.
 *
 * This class consumes messages from two Kafka topics:
 * <ul>
 *   <li>"notifications" - Direct notification events requiring immediate processing</li>
 *   <li>"notification-requests" - Rule-based notification requests with custom logic</li>
 * </ul>
 *
 * Each message is processed with:
 * <ul>
 *   <li>Distributed tracing via OpenTelemetry spans for observability</li>
 *   <li>Contextual logging using MDC (Mapped Diagnostic Context) with traceId and eventId</li>
 *   <li>Manual acknowledgment to ensure messages are processed before offset commit</li>
 *   <li>Exception handling with error logging and span status updates</li>
 * </ul>
 *
 * @see NotificationProcessingService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationProcessingService processingService;
    private final Tracer tracer;

    /**
     * Consumes direct notification events from the "notifications" Kafka topic.
     *
     * Processes each notification event by delegating to {@link NotificationProcessingService}.
     * A unique trace ID is generated for each event and stored in MDC for distributed tracing.
     * The message is acknowledged only after successful processing.
     *
     * @param event the notification event to be processed, containing notification details
     * @param ack manual acknowledgment handle used to commit the Kafka offset after processing
     * @throws Exception if processing fails during service invocation or message handling
     */
    @KafkaListener(topics = "notifications",
            groupId = "notification-event-group",
            containerFactory = "directFactory")
    public void consumeDirectNotificationEvent(NotificationEvent event, Acknowledgment ack) throws Exception {

        log.info("RECEIVED NotificationEvent from Kafka topic 'notifications': {}", event);

        var traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventId", null);

        Span span = createSpan("process.direct.notification", event.type(), traceId, null);

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
            MDC.clear();
            span.end();
        }
    }

    /**
     * Creates an OpenTelemetry span with the specified attributes.
     *
     * Constructs a new tracing span with event type, trace ID, and optional event ID.
     * These attributes are used for distributed tracing and observability purposes.
     *
     * @param spanName the name of the span to be created
     * @param eventType the type of event being traced
     * @param traceId the unique trace identifier for correlating logs and traces
     * @param eventId the unique event identifier (optional, may be null)
     * @return a new Span instance with the specified attributes
     */
    private Span createSpan(String spanName, String eventType,
                            String traceId, String eventId) {
        var spanBuilder = tracer.spanBuilder(spanName)
                .setAttribute("event.type", eventType)
                .setAttribute("trace.id", traceId);
        if (eventId != null) {
            spanBuilder.setAttribute("event.id", eventId);
        }
        return spanBuilder.startSpan();
    }

    /**
     * Consumes rule-based notification requests from the "notification-requests" Kafka topic.
     *
     * Processes each notification request event with custom business logic via
     * {@link NotificationProcessingService}. A unique trace ID and event ID are stored in MDC
     * for comprehensive request tracing. The message is acknowledged only after successful processing.
     *
     * @param event the notification request event containing event ID and processing details
     * @param ack manual acknowledgment handle used to commit the Kafka offset after processing
     * @throws Exception if processing fails during service invocation or message handling
     */
    @KafkaListener(topics = "notification-requests",
            groupId = "delivery-group",
            containerFactory = "ruleFactory")
    public void consumeNotificationRequest(NotificationRequestEvent event,
                                           Acknowledgment ack) throws Exception {

        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventId", event.eventId());

        Span span = createSpan("process.rule.notification",
                event.type(),
                event.eventId(),
                event.eventId());

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
            MDC.clear();
            span.end();
        }
    }
}