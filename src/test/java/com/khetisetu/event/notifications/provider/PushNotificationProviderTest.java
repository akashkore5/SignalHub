package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.Notification;
import com.khetisetu.event.notifications.service.UserTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PushNotificationProviderTest {

    @Mock
    private UserTokenService userTokenService;

    private PushNotificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PushNotificationProvider(userTokenService);
    }

    @Test
    void send_ShouldMarkAsSkipped_WhenNoTokensFound() {
        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(new HashMap<>())
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of());

        provider.send(event, notificationRecord);

        assertEquals("SKIPPED", notificationRecord.getStatus());
        assertEquals("No FCM token found", notificationRecord.getErrorMessage());
        verify(userTokenService).getFcmTokens("user_123");
    }

    @Test
    void send_ShouldSendToAllDevices_WhenMultipleTokensPresent() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Hello");
        params.put("body", "World");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("token_laptop", "token_mobile"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any())).thenReturn("msg_id");

            provider.send(event, notificationRecord);

            // Both tokens should have been sent to
            verify(firebaseMessaging, times(2)).send(any());
        }
    }

    @Test
    void send_ShouldInvalidateStaleToken_AndSucceedForOtherDevices() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Hello");
        params.put("body", "World");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("stale_token", "good_token"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            // First token is stale, second succeeds
            FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
            when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(unregisteredException.getMessage()).thenReturn("Requested entity was not found.");

            when(firebaseMessaging.send(any()))
                    .thenThrow(unregisteredException) // First call: stale
                    .thenReturn("msg_id"); // Second call: success

            // Should NOT throw — one device succeeded
            provider.send(event, notificationRecord);

            // Stale token should have been invalidated
            verify(userTokenService).invalidateToken("user_123", "stale_token");
            // Status should NOT be FAILED since one succeeded
            assertNotEquals("FAILED_UNREGISTERED", notificationRecord.getStatus());
        }
    }

    @Test
    void send_ShouldMarkAsFailed_WhenAllTokensAreStale() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("title", "Hello");
        params.put("body", "World");

        NotificationRequestEvent event = NotificationRequestEvent.builder()
                .recipient("user_123")
                .params(params)
                .build();
        Notification notificationRecord = new Notification();

        when(userTokenService.getFcmTokens("user_123")).thenReturn(List.of("stale1", "stale2"));

        try (MockedStatic<FirebaseMessaging> mockedFirebase = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            mockedFirebase.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            FirebaseMessagingException unregisteredException = mock(FirebaseMessagingException.class);
            when(unregisteredException.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
            when(unregisteredException.getMessage()).thenReturn("Not found");

            when(firebaseMessaging.send(any())).thenThrow(unregisteredException);

            provider.send(event, notificationRecord);

            assertEquals("FAILED_UNREGISTERED", notificationRecord.getStatus());
            verify(userTokenService).invalidateToken("user_123", "stale1");
            verify(userTokenService).invalidateToken("user_123", "stale2");
        }
    }
}
