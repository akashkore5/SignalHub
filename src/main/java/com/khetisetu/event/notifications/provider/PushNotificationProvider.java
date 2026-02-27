package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.UserTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component("PUSH")
@RequiredArgsConstructor
@Slf4j
public class PushNotificationProvider implements NotificationProvider {

    private final UserTokenService userTokenService;

    /**
     * FCM error codes that indicate the token is permanently invalid.
     */
    private static final Set<MessagingErrorCode> STALE_TOKEN_ERRORS = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    @Override
    public String getType() {
        return "PUSH";
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void send(NotificationRequestEvent event,
            com.khetisetu.event.notifications.model.Notification notificationRecord) {
        List<String> tokens = userTokenService.getFcmTokens(event.recipient());

        if (tokens.isEmpty()) {
            log.warn("No FCM tokens found for user: {}. Skipping push notification.", event.recipient());
            notificationRecord.setStatus("SKIPPED");
            notificationRecord.setErrorMessage("No FCM token found");
            return;
        }

        String title = event.params().getOrDefault("title", "Notification");
        String body = event.params().getOrDefault("body", "");

        int successCount = 0;
        int staleCount = 0;
        Exception lastTransientError = null;

        for (String token : tokens) {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build());

            if (event.params() != null) {
                event.params().forEach((k, v) -> {
                    if (v != null)
                        messageBuilder.putData(k, String.valueOf(v));
                });
            }

            try {
                String response = FirebaseMessaging.getInstance().send(messageBuilder.build());
                log.info("Successfully sent FCM message to device (token: {}...): {}",
                        token.substring(0, Math.min(10, token.length())), response);
                successCount++;
            } catch (FirebaseMessagingException e) {
                MessagingErrorCode errorCode = e.getMessagingErrorCode();
                log.error("FCM error for user {} (token: {}...): code={}, message={}",
                        event.recipient(), token.substring(0, Math.min(10, token.length())),
                        errorCode, e.getMessage());

                if (errorCode != null && STALE_TOKEN_ERRORS.contains(errorCode)) {
                    log.warn("Stale FCM token for user {}. Removing from DB.", event.recipient());
                    userTokenService.invalidateToken(event.recipient(), token);
                    staleCount++;
                } else {
                    lastTransientError = e;
                }
            }
        }

        if (successCount > 0) {
            log.info("Push notification sent to {}/{} devices for user {}",
                    successCount, tokens.size(), event.recipient());
        } else if (staleCount == tokens.size()) {
            notificationRecord.setStatus("FAILED_UNREGISTERED");
            notificationRecord.setErrorMessage("All " + staleCount + " FCM tokens were stale. Removed.");
        } else if (lastTransientError != null) {
            throw new RuntimeException("FCM Send Failed (transient)", lastTransientError);
        }
    }
}
