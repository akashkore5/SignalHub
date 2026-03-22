package com.khetisetu.event.notifications.service;

import com.khetisetu.event.logs.service.LogService;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.provider.NotificationProvider;
import com.khetisetu.event.notifications.repository.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationProcessingServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private MeterRegistry meterRegistry;
    @Mock
    private Counter counter;
    @Mock
    private NotificationProvider pushProvider;
    @Mock
    private NotificationProvider emailProvider;

    @Mock
    LogService logService;

    private NotificationProcessingService service;

    @BeforeEach
    void setUp() {
        Map<String, NotificationProvider> providers = new HashMap<>();
        providers.put("PUSH", pushProvider);
        providers.put("EMAIL", emailProvider);

        doNothing().when(logService).storeLog(any(), anyString(), any(), anyString(), anyString());

        service = new NotificationProcessingService(
                notificationRepository,
                providers,
                kafkaTemplate,
                redisTemplate,
                meterRegistry);
        service.logService = logService; // Inject mock log service

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);
    }

    @Test
    void processRequest_ShouldSendToBoth_WhenBothFlagsAreTrue() throws Exception {
        // Arrange
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId("evt_123")
                .userId("usr_456")
                .recipient("test@example.com")
                .sendPush(true)
                .sendEmail(true)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null); // Rate limit null
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        service.process(event);

        // Assert
        verify(pushProvider, times(1)).send(eq(event), any(Notification.class));
        verify(emailProvider, times(1)).send(eq(event), any(Notification.class));
    }

    @Test
    void processRequest_ShouldNotBlockEmail_WhenPushFails() throws Exception {
        // Arrange
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId("evt_123")
                .userId("usr_456")
                .recipient("test@example.com")
                .sendPush(true)
                .sendEmail(true)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        // Mock PUSH failure
        doThrow(new RuntimeException("Push failed")).when(pushProvider).send(eq(event), any(Notification.class));

        // Act
        assertThrows(RuntimeException.class, () -> service.process(event));

        // Assert
        verify(pushProvider, times(1)).send(eq(event), any(Notification.class));
        verify(emailProvider, times(1)).send(eq(event), any(Notification.class));

        // Verify both notifications were created and status updated
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeast(2)).save(captor.capture());

        boolean hasFailedPush = captor.getAllValues().stream()
                .anyMatch(n -> "PUSH".equals(n.getType()) && "FAILED".equals(n.getStatus()));
        boolean hasSentEmail = captor.getAllValues().stream()
                .anyMatch(n -> "EMAIL".equals(n.getType()) && "SENT".equals(n.getStatus()));

        assertTrue(hasFailedPush, "Should have a failed PUSH notification record");
        assertTrue(hasSentEmail, "Should have a sent EMAIL notification record");
    }

    @Test
    void processRequest_ShouldRelyOnType_WhenFlagsAreMissing() throws Exception {
        // Arrange
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .eventId("evt_123")
                .userId("usr_456")
                .recipient("test@example.com")
                .type("EMAIL") // Explicit type, flags false
                .sendPush(false)
                .sendEmail(false)
                .build();

        when(valueOperations.get(anyString())).thenReturn(null);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        service.process(event);

        // Assert
        verify(emailProvider, times(1)).send(eq(event), any(Notification.class));
        verify(pushProvider, never()).send(any(), any());
    }
}
