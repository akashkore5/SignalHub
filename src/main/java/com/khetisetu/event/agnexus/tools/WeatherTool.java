package com.khetisetu.event.agnexus.tools;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WeatherTool implements Tool {

    @Value("${openweathermap.api.key:}")
    private String apiKey;

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "WeatherTool";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        log.info("Executing WeatherTool with params: {}", params);

        if (apiKey == null || apiKey.isEmpty()) {
            return new ToolResult(null, false, "Weather API Key is not configured.");
        }

        String location = (String) params.get("location");
        if (location == null) {
            return new ToolResult(null, false, "Location is required for WeatherTool.");
        }

        try {
            String url = String.format("%s?q=%s&appid=%s&units=metric", BASE_URL, location, apiKey);
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return new ToolResult(response, true, "Success");
        } catch (Exception e) {
            log.error("Error fetching weather data", e);
            return new ToolResult(null, false, "Error: " + e.getMessage());
        }
    }
}
