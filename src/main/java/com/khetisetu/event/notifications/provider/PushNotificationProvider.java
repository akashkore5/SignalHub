package com.khetisetu.event.notifications.provider;

import com.google.firebase.messaging.*;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.service.NotificationTemplateService;
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
    private final com.khetisetu.event.notifications.service.NotificationTemplateService templateService;
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

    public PushNotificationProvider(UserTokenService userTokenService,
            com.khetisetu.event.notifications.service.NotificationTemplateService templateService,
            MeterRegistry meterRegistry) {
        this.userTokenService = userTokenService;
        this.templateService = templateService;
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

        // Prefer explicit title/body in params (direct/custom pushes). Otherwise
        // render the named template (e.g. "new_job") so trigger-based pushes carry
        // proper, localized text instead of a generic "Notification".
        String title = event.params().get("title");
        String body = event.params().get("body");
        if (title == null || title.isBlank() || body == null || body.isBlank()) {
            NotificationTemplateService.Content resolved = templateService.resolve(
                    event.templateName(), event.language(), event.params());
            if (title == null || title.isBlank()) {
                title = resolved.title();
            }
            if (body == null || body.isBlank()) {
                body = resolved.body();
            }
        }
        if (title == null || title.isBlank()) {
            title = "Notification";
        }
        if (body == null) {
            body = "";
        }

        // Store in notification record
        notificationRecord.setSubject(title);
        notificationRecord.setContent(body);

        // DATA-ONLY FCM message: No top-level 'notification' payload.
        // Our FCM tokens are web push tokens (from browser Firebase SDK).
        // Sending a 'notification' payload to web push tokens causes TWA apps
        // to silently drop or misroute notifications. Instead, we put everything
        // in the 'data' payload and let the service worker (sw.js) handle display.

        String image = event.params().get("image");
        String clickUrl = event.params().get("clickUrl");
        String tag = event.params().get("tag");

        // Build multicast message with data-only payload
        MulticastMessage.Builder multicastBuilder = MulticastMessage.builder()
                .addAllTokens(tokens);

        // Add all params as data payload (for sw.js push handler)
        if (event.params() != null) {
            event.params().forEach((k, v) -> {
                if (v != null)
                    multicastBuilder.putData(k, String.valueOf(v));
            });
        }

        // Ensure title, body, and url are always in data payload
        multicastBuilder.putData("title", title);
        multicastBuilder.putData("body", body);
        if (clickUrl != null) {
            multicastBuilder.putData("url", clickUrl);
        }
        if (image != null && !image.isBlank()) {
            multicastBuilder.putData("image", image);
        }

        // Webpush Config — controls how the browser receives the push
        WebpushNotification.Builder webpushNotif = WebpushNotification.builder()
                .setTitle(title)
                .setBody(body);

        String icon = event.params().get("icon");
        if (icon != null && !icon.isBlank()) {
            webpushNotif.setIcon(icon);
        } else {
            webpushNotif.setIcon("/icons/icon-192x192.png");
        }

        String badge = event.params().get("badge");
        if (badge != null && !badge.isBlank()) {
            webpushNotif.setBadge(badge);
        }

        if (tag != null && !tag.isBlank()) {
            webpushNotif.setTag(tag);
        }

        multicastBuilder.setWebpushConfig(WebpushConfig.builder()
                .setNotification(webpushNotif.build())
                .putHeader("Urgency", "high") // High urgency for immediate delivery
                .setFcmOptions(WebpushFcmOptions.withLink(clickUrl != null ? clickUrl : "/"))
                .build());

        // Android Config — HIGH priority with notification payload for native Android apps.
        // Native Android apps need a 'notification' payload to auto-display push notifications.
        // The data payload is also sent for custom handling in onMessageReceived().
        AndroidNotification.Builder androidNotifBuilder = AndroidNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setIcon("ic_notification")
                .setSound("default")
                .setChannelId("khetisetu_notifications")
                .setClickAction("FLUTTER_NOTIFICATION_CLICK");

        if (image != null && !image.isBlank()) {
            androidNotifBuilder.setImage(image);
        }
        if (tag != null && !tag.isBlank()) {
            androidNotifBuilder.setTag(tag);
        }

        multicastBuilder.setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(androidNotifBuilder.build())
                .build());

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
