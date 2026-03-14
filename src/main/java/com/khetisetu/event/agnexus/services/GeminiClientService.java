package com.khetisetu.event.agnexus.services;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class GeminiClientService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=";

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateText(String prompt) {
        return generateContent(prompt, null);
    }

    public String analyzeImage(String imageUrl, String prompt) {
        return generateContent(prompt, imageUrl);
    }

    private String generateContent(String prompt, String imageUrl) {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Gemini API key is missing. Returning placeholder response.");
            return "AI feedback for: " + prompt + (imageUrl != null ? " [with image]" : "");
        }

        try {
            JSONObject requestBody = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();

            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);

            if (imageUrl != null) {
                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                // Note: For simplicity, we're assuming the user might pass a base64 or public
                // URL
                // In a real Gemini API call, you often use fileData or inlineData
                // For this implementation, we'll treat it as a text-based image description if
                // it's not base64
                textPart.put("text", prompt + "\n[Image Context: " + imageUrl + "]");
            }

            content.put("parts", parts);
            contents.put(content);
            requestBody.put("contents", contents);

            String response = restTemplate.postForObject(GEMINI_API_URL + apiKey, requestBody.toString(), String.class);

            JSONObject jsonResponse = new JSONObject(response);
            return jsonResponse.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");

        } catch (Exception e) {
            log.error("Error calling Gemini API", e);
            return "Error generating response: " + e.getMessage();
        }
    }
}
