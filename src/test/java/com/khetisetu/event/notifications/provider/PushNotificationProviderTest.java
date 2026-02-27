package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.*;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.service.UserTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushNotificationProviderTest {

    @Mock
    private UserTokenService userTokenService;

    private MeterRegistry meterRegistry;
    private PushNotificationProvider provider;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        provider = new PushNotificationProvider(userTokenService, meterRegistry);
    }

    @Test
    void send_ShouldMarkAsSkipped_WhenNoTokensFound() {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(new HashMap<>())
                .build();
        Notification notif = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of());

        provider.send(event, notif);

        assertEquals("SKIPPED", notif.getStatus());
        assertEquals(1.0, meterRegistry.counter("push.send", "result", "skipped").count());
    }

    @Test
    void send_ShouldUseMulticast_ForMultipleTokens() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Hello");
        params.put("body", "World");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notif = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("token1", "token2"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging fm = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(fm);

            SendResponse successResp = mock(SendResponse.class);
            when(successResp.isSuccessful()).thenReturn(true);

            BatchResponse batchResponse = mock(BatchResponse.class);
            when(batchResponse.getSuccessCount()).thenReturn(2);
            when(batchResponse.getFailureCount()).thenReturn(0);
            when(batchResponse.getResponses()).thenReturn(List.of(successResp, successResp));

            when(fm.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            provider.send(event, notif);

            verify(fm, times(1)).sendEachForMulticast(any(MulticastMessage.class));
            assertEquals(2.0, meterRegistry.counter("push.send", "result", "success").count());
        }
    }

    @Test
    void send_ShouldInvalidateStaleTokens_AndSucceedForOthers() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Test");
        params.put("body", "Body");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notif = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("stale_tok", "good_token"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging fm = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(fm);

            FirebaseMessagingException staleEx = mock(FirebaseMessagingException.class);
            when(staleEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(staleEx.getMessage()).thenReturn("Not found");

            SendResponse staleResp = mock(SendResponse.class);
            when(staleResp.isSuccessful()).thenReturn(false);
            when(staleResp.getException()).thenReturn(staleEx);

            SendResponse goodResp = mock(SendResponse.class);
            when(goodResp.isSuccessful()).thenReturn(true);

            BatchResponse batchResponse = mock(BatchResponse.class);
            when(batchResponse.getSuccessCount()).thenReturn(1);
            when(batchResponse.getFailureCount()).thenReturn(1);
            when(batchResponse.getResponses()).thenReturn(List.of(staleResp, goodResp));

            when(fm.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            provider.send(event, notif);

            verify(userTokenService).invalidateToken("user_123", "stale_tok");
            assertNotEquals("FAILED_UNREGISTERED", notif.getStatus());

            assertEquals(1.0, meterRegistry.counter("push.send", "result", "success").count());
            assertEquals(1.0, meterRegistry.counter("push.send", "result", "stale_token").count());
        }
    }

    @Test
    void send_ShouldMarkFailed_WhenAllTokensStale() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Test");
        params.put("body", "Body");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notif = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("stale1", "stale2"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging fm = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(fm);

            FirebaseMessagingException staleEx = mock(FirebaseMessagingException.class);
            when(staleEx.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(staleEx.getMessage()).thenReturn("Not found");

            SendResponse staleResp = mock(SendResponse.class);
            when(staleResp.isSuccessful()).thenReturn(false);
            when(staleResp.getException()).thenReturn(staleEx);

            BatchResponse batchResponse = mock(BatchResponse.class);
            when(batchResponse.getSuccessCount()).thenReturn(0);
            when(batchResponse.getFailureCount()).thenReturn(2);
            when(batchResponse.getResponses()).thenReturn(List.of(staleResp, staleResp));

            when(fm.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(batchResponse);

            provider.send(event, notif);

            assertEquals("FAILED_UNREGISTERED", notif.getStatus());
            verify(userTokenService).invalidateToken("user_123", "stale1");
            verify(userTokenService).invalidateToken("user_123", "stale2");
        }
    }
}
