package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.*;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.UserTokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component("PUSH")
@Slf4j
public class PushNotificationProvider implements NotificationProvider {

    private final UserTokenService userTokenService;
    private final Counter successCounter;
    private final Counter staleCounter;
    private final Counter transientFailCounter;
    private final Counter skippedCounter;

    /**
     * FCM error codes that indicate the token is permanently invalid.
     */
    private static final Set<MessagingErrorCode> STALE_TOKEN_ERRORS = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH);

    public PushNotificationProvider(UserTokenService userTokenService, MeterRegistry meterRegistry) {
        this.userTokenService = userTokenService;
        this.successCounter = Counter.builder("push.send")
                .tag("result", "success")
                .description("FCM messages sent successfully")
                .register(meterRegistry);
        this.staleCounter = Counter.builder("push.send")
                .tag("result", "stale_token")
                .description("FCM messages failed due to stale/unregistered token")
                .register(meterRegistry);
        this.transientFailCounter = Counter.builder("push.send")
                .tag("result", "transient_error")
                .description("FCM messages failed due to transient error")
                .register(meterRegistry);
        this.skippedCounter = Counter.builder("push.send")
                .tag("result", "skipped")
                .description("Push notifications skipped (no token)")
                .register(meterRegistry);
    }

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
            log.warn("No FCM tokens found for user: {}. Skipping push.", event.recipient());
            notificationRecord.setStatus("SKIPPED");
            notificationRecord.setErrorMessage("No FCM token found");
            skippedCounter.increment();
            return;
        }

        String title = event.params().getOrDefault("title", "Notification");
        String body = event.params().getOrDefault("body", "");

        // Build rich notification with optional image and icon
        Notification.Builder notifBuilder = Notification.builder()
                .setTitle(title)
                .setBody(body);

        String image = event.params().get("image");
        if (image != null && !image.isBlank()) {
            notifBuilder.setImage(image);
        }

        // Build multicast message for batch delivery
        MulticastMessage.Builder multicastBuilder = MulticastMessage.builder()
                .setNotification(notifBuilder.build())
                .addAllTokens(tokens);

        // Add all params as data payload (for sw.js click handling)
        if (event.params() != null) {
            event.params().forEach((k, v) -> {
                if (v != null)
                    multicastBuilder.putData(k, String.valueOf(v));
            });
        }

        // Add clickUrl as data if present
        String clickUrl = event.params().get("clickUrl");
        if (clickUrl != null) {
            multicastBuilder.putData("url", clickUrl);
        }

        try {
            BatchResponse batchResponse = FirebaseMessaging.getInstance()
                    .sendEachForMulticast(multicastBuilder.build());

            int successCount = batchResponse.getSuccessCount();
            int failureCount = batchResponse.getFailureCount();

            log.info("FCM multicast result for user {}: success={}, failures={}",
                    event.recipient(), successCount, failureCount);
            successCounter.increment(successCount);

            // Process individual failures
            if (failureCount > 0) {
                List<SendResponse> responses = batchResponse.getResponses();
                int staleCount = 0;
                Exception lastTransientError = null;

                for (int i = 0; i < responses.size(); i++) {
                    SendResponse resp = responses.get(i);
                    if (!resp.isSuccessful()) {
                        FirebaseMessagingException ex = resp.getException();
                        MessagingErrorCode errorCode = ex != null ? ex.getMessagingErrorCode() : null;

                        if (errorCode != null && STALE_TOKEN_ERRORS.contains(errorCode)) {
                            String staleToken = tokens.get(i);
                            log.warn("Stale token for user {} (token: {}...): {}",
                                    event.recipient(),
                                    staleToken.substring(0, Math.min(10, staleToken.length())),
                                    errorCode);
                            userTokenService.invalidateToken(event.recipient(), staleToken);
                            staleCount++;
                            staleCounter.increment();
                        } else {
                            log.error("Transient FCM error for user {}: {}",
                                    event.recipient(), ex != null ? ex.getMessage() : "unknown");
                            lastTransientError = ex;
                            transientFailCounter.increment();
                        }
                    }
                }

                if (successCount == 0 && staleCount == tokens.size()) {
                    notificationRecord.setStatus("FAILED_UNREGISTERED");
                    notificationRecord.setErrorMessage(
                            "All " + staleCount + " FCM tokens stale. Removed.");
                } else if (successCount == 0 && lastTransientError != null) {
                    throw new RuntimeException("FCM Send Failed (transient)", lastTransientError);
                }
            }
        } catch (FirebaseMessagingException e) {
            log.error("FCM multicast call failed for user {}: {}", event.recipient(), e.getMessage());
            transientFailCounter.increment(tokens.size());
            throw new RuntimeException("FCM Send Failed", e);
        }
    }
}
