package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.NotNull;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Dead Letter Queue (DLQ) handler component that implements Spring Kafka's CommonErrorHandler.
 * This class is responsible for handling exceptions that occur during Kafka message processing.
 * When an error is encountered, it attempts to forward the failed message to a designated DLQ topic
 * for later analysis or reprocessing, preventing message loss and allowing for debugging.
 */
@Component("dlqHandler")
@RequiredArgsConstructor
@Slf4j
public class DlqHandler implements CommonErrorHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Handles a single exception that occurred during Kafka message processing.
     * Logs the error details, attempts to send the failed record to the 'notification-dlq' topic,
     * and returns true if successful (indicating the error was handled), or false if sending to DLQ failed.
     *
     * @param thrownException the exception that was thrown during processing
     * @param record the Kafka ConsumerRecord that caused the exception
     * @param consumer the Kafka consumer instance
     * @param container the message listener container
     * @return true if the record was successfully sent to DLQ, false otherwise
     */
    @Override
    public boolean handleOne(Exception thrownException, ConsumerRecord<?, ?> record, @NotNull Consumer<?, ?> consumer,
                             @NotNull MessageListenerContainer container) {

        log.error("Handling error for record offset {}: {}",
                record.offset(), Arrays.toString(thrownException.getStackTrace()));
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
            log.error("Failed to send to DLQ {}", Arrays.toString(e.getStackTrace()));
            return false; // Failed to handle
        }
    }
}