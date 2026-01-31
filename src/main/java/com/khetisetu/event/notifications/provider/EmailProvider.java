package com.khetisetu.event.notifications.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.khetisetu.event.notifications.dto.NotificationRequestEvent;
import com.khetisetu.event.notifications.model.EmailSenderConfig;
import com.khetisetu.event.notifications.model.Notification;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component("EMAIL")
@RequiredArgsConstructor
@Slf4j
public class EmailProvider implements NotificationProvider {

    private final BrevoEmailProvider brevoEmailProvider;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Value("${email.enabled:true}")
    private boolean enabled;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("dd MMM yyyy, hh:mm a")
            .withZone(ZoneId.systemDefault());

    private final Map<String, String> subjectsCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("EmailProvider initialized for type: EMAIL");
    }

    @Override
    public String getType() {
        return "EMAIL";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(NotificationRequestEvent event, Notification notification) throws Exception {
        log.info("Sending EMAIL to {} using template {}", event.recipient(), event.templateName());

        // 1. Validate sender config
        EmailSenderConfig senderConfig = event.senderConfig();
        if (senderConfig == null) {
            log.error("Email sender config is null for event: {}", event);
            throw new IllegalArgumentException("Email sender configuration is required");
        }

        // 2. Render HTML template
        String htmlContent = renderEmailTemplate(
                event.templateName(),
                event.params(),
                event.language() != null ? event.language() : "en");

        // 3. Send via Brevo
        try {
            brevoEmailProvider.sendTransactionalEmail(
                    senderConfig.getSenderEmail(),
                    senderConfig.getSenderName(),
                    event.recipient(),
                    getSubject(event),
                    htmlContent);
            log.info("Email sent successfully to {} via Brevo", event.recipient());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", event.recipient(), e.getMessage(), e);
            throw e;
        }
    }

    private String renderEmailTemplate(String templateName, Map<String, String> params, String language) {
        try {
            // Template path: templates/{language}/{templateName}.html
            String templatePath = language + "/" + templateName;

            // Create ThymeLeaf context
            Context context = new Context(Locale.forLanguageTag(language));

            // Add all parameters
            if (params != null) {
                params.forEach(context::setVariable);
            }

            // Add common variables
            context.setVariable("createdDate", DATE_FORMATTER.format(Instant.now()));
            context.setVariable("year", java.time.Year.now().getValue());

            // Render
            String html = templateEngine.process(templatePath, context);
            log.debug("Rendered template: {} with {} parameters", templatePath, params != null ? params.size() : 0);

            return html;
        } catch (Exception e) {
            log.error("Failed to render email template: {} for language: {}", templateName, language, e);
            throw new RuntimeException("Email template rendering failed: " + templateName, e);
        }
    }

    private String getSubject(NotificationRequestEvent event) {
        // 1. Priority: Params
        if (event.params() != null && event.params().containsKey("subject")) {
            return event.params().get("subject");
        }

        // 2. Secondary: Load from notification_templates.json
        String templateName = event.templateName();
        String language = (event.language() != null && !event.language().isEmpty()) ? event.language() : "en";
        String cacheKey = language + "_" + templateName;

        if (subjectsCache.containsKey(cacheKey)) {
            return subjectsCache.get(cacheKey);
        }

        try {
            String path = "templates/" + language + "/notification_templates.json";
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                JsonNode root = objectMapper.readTree(resource.getInputStream());
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        if (node.has("name") && node.get("name").asText().equals(templateName)) {
                            String subject = node.get("subject").asText();
                            subjectsCache.put(cacheKey, subject);
                            return subject;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load subject from JSON mapping for {} (lang: {})", templateName, language);
        }

        // 3. Fallback: Pretty print template name
        return "Kheti Setu: " + templateName.replace("_", " ");
    }
}
