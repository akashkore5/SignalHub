package com.khetisetu.event.notifications.provider;

import com.khetisetu.event.notifications.provider.EmailSender;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class BrevoEmailProvider implements EmailSender {

        private static final Logger logger = LoggerFactory.getLogger(BrevoEmailProvider.class);

        @Value("${brevo.apikey}")
        private String API_KEY;

        private static final String BASE_URL = "https://api.brevo.com/v3";
        private static final String BREVO_CB = "brevoService";

        private final OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

        @Override
        public String getProviderName() {
                return "BREVO";
        }

        @Override
        @CircuitBreaker(name = BREVO_CB, fallbackMethod = "fallbackSendEmail")
        public void sendEmail(String fromEmail, String fromName, String toEmail, String subject, String htmlBody) {
                try {
                        sendTransactionalEmail(fromEmail, fromName, toEmail, subject, htmlBody);
                } catch (Exception e) {
                        throw new RuntimeException("Failed to send email via Brevo", e);
                }
        }

        public void fallbackSendEmail(String fromEmail, String fromName, String toEmail, String subject,
                        String htmlBody, Throwable t) {
                logger.error("Fallback: circuit breaker open or error for Brevo. Reason: {}", t.getMessage());
                // In a real generic fallback, we might call another provider here.
                // For now, we propagate or log. Throwing ensures retry or DLQ.
                throw new RuntimeException("Brevo unavailable (Circuit Breaker): " + t.getMessage(), t);
        }

        /**
         * Send a transactional email directly via HTTP
         */
        private void sendTransactionalEmail(String fromEmail, String fromName, String toEmail, String subject,
                        String htmlBody) throws Exception {
                JSONObject sender = new JSONObject()
                                .put("name", fromName)
                                .put("email", fromEmail);

                JSONObject to = new JSONObject()
                                .put("email", toEmail);

                JSONObject payload = new JSONObject()
                                .put("sender", sender)
                                .put("to", new org.json.JSONArray().put(to))
                                .put("subject", subject)
                                .put("htmlContent", htmlBody)
                                .put("textContent", stripHtml(htmlBody));

                RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toString());

                Request request = new Request.Builder()
                                .url(BASE_URL + "/smtp/email")
                                .addHeader("api-key", API_KEY)
                                .addHeader("accept", "application/json")
                                .post(body)
                                .build();

                try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                                String error = response.body() != null ? response.body().string() : "unknown";
                                logger.error("Brevo transactional email failed: {}", error);
                                throw new RuntimeException("Brevo API error: " + error);
                        }
                        logger.info("Transactional email sent successfully to {}", toEmail);
                }
        }

        private String stripHtml(String html) {
                return html.replaceAll("<[^>]*>", "");
        }

        // Kept for backward compatibility if needed, or remove if unused.
        public void createCampaign(String name, String subject, String fromName, String fromEmail, String htmlContent,
                        int[] listIds, String scheduleAt) throws Exception {
                // ... existing implementation if needed ...
                // For brevity, assuming only transactional is prioritized as per "email
                // functionality" context.
                JSONObject sender = new JSONObject()
                                .put("name", fromName)
                                .put("email", fromEmail);

                JSONObject recipients = new JSONObject()
                                .put("listIds", listIds);

                JSONObject payload = new JSONObject()
                                .put("name", name)
                                .put("subject", subject)
                                .put("sender", sender)
                                .put("type", "classic")
                                .put("htmlContent", htmlContent)
                                .put("recipients", recipients)
                                .put("scheduledAt", scheduleAt); // format: "YYYY-MM-DD HH:mm:ss"

                RequestBody body = RequestBody.create(MediaType.parse("application/json"), payload.toString());

                Request request = new Request.Builder()
                                .url(BASE_URL + "/emailCampaigns")
                                .addHeader("api-key", API_KEY)
                                .addHeader("accept", "application/json")
                                .post(body)
                                .build();

                try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful()) {
                                String error = response.body() != null ? response.body().string() : "unknown";
                                logger.error("Brevo campaign creation failed: {}", error);
                                throw new RuntimeException("Brevo campaign creation failed: " + error);
                        }
                        logger.info("Brevo campaign created successfully: {}", name);
                }
        }
}
