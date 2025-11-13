package com.khetisetu.event.notifications.consumer;

import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Component("dlqHandler")
@RequiredArgsConstructor
public class DlqHandler implements CommonErrorHandler {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public Object handleError(Message<?> message, Exception e) {
        var event = (NotificationRequestEvent) message.getPayload();
        kafkaTemplate.send("notification-dlq", event);
        System.out.println("Sent to DLQ: {}"+ event.eventId());
        return null;
    }

}