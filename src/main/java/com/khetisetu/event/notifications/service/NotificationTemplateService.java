package com.khetisetu.event.notifications.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves notification text (title/body) for non-email channels (PUSH, etc.)
 * from the per-language {@code notification_templates.json} files, applying
 * {@code {{placeholder}}} substitution from the event params.
 *
 * <p>For PUSH we map the template's {@code subject} → title and
 * {@code content} → body. Falls back to English, then to a prettified template
 * name, so a missing template never produces an empty notification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationTemplateService {

    private final ObjectMapper objectMapper;

    public record Content(String title, String body) {
    }

    // cacheKey -> raw (un-substituted) Content
    private final Map<String, Content> rawCache = new ConcurrentHashMap<>();

    /**
     * Resolves the display title/body for a template, with placeholders filled in.
     */
    public Content resolve(String templateName, String language, Map<String, String> params) {
        String lang = (language != null && !language.isEmpty()) ? language : "en";
        Content raw = loadRaw(templateName, lang);
        if (raw == null && !"en".equals(lang)) {
            raw = loadRaw(templateName, "en");
        }
        if (raw == null) {
            String pretty = "KhetiSetu: " + (templateName != null ? templateName.replace("_", " ") : "Notification");
            return new Content(pretty, "");
        }
        return new Content(substitute(raw.title(), params), substitute(raw.body(), params));
    }

    private Content loadRaw(String templateName, String language) {
        if (templateName == null) {
            return null;
        }
        String cacheKey = language + "_" + templateName;
        Content cached = rawCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            String path = "templates/" + language + "/notification_templates.json";
            ClassPathResource resource = new ClassPathResource(path);
            if (resource.exists()) {
                JsonNode root = objectMapper.readTree(resource.getInputStream());
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        if (node.has("name") && node.get("name").asText().equals(templateName)) {
                            String title = node.hasNonNull("subject") ? node.get("subject").asText() : "";
                            String body = node.hasNonNull("content") ? node.get("content").asText() : "";
                            Content content = new Content(title, body);
                            rawCache.put(cacheKey, content);
                            return content;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load template '{}' (lang: {}): {}", templateName, language, e.getMessage());
        }
        return null;
    }

    private String substitute(String text, Map<String, String> params) {
        if (text == null || text.isEmpty() || params == null || params.isEmpty()) {
            return text == null ? "" : text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }
}
