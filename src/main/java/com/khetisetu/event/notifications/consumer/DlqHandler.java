package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

@Component("dlqHandler")
@RequiredArgsConstructor
@Slf4j
public class DlqHandler implements CommonErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record, Consumer<?, ?> consumer,
            MessageListenerContainer container) {
        log.error("Handling error for record offset {}: {}", record.offset(), thrownException.getMessage());
        try {
            // Forward to DLQ
            if (record.value() instanceof NotificationRequestEvent event) {
                kafkaTemplate.send("notification-dlq", event);
                log.info("Sent record to DLQ: {}", event.eventId());
            } else {
                kafkaTemplate.send("notification-dlq", record.value());
                log.info("Sent raw record to DLQ");
            }
            return true; // We handled it (moved to DLQ)
        } catch (Exception e) {
            log.error("Failed to send to DLQ", e);
            return false; // Failed to handle
        }
    }
}