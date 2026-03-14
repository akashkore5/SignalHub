package com.khetisetu.event.agnexus.tools;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MandiPriceTool implements Tool {

    @Value("${data.gov.in.api.key:}")
    private String apiKey;

    private static final String BASE_URL = "https://api.data.gov.in/resource/35985678-0d79-46b4-9ed6-6f13308a1d24";
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getName() {
        return "MandiPriceTool";
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        log.info("Executing MandiPriceTool with params: {}", params);

        if (apiKey == null || apiKey.isEmpty()) {
            return new ToolResult(null, false, "API Key (data.gov.in.api.key) is not configured.");
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .queryParam("api-key", apiKey)
                    .queryParam("format", "json");

            // Map input params to API filters
            if (params.containsKey("state")) {
                builder.queryParam("filters[State]", params.get("state"));
            }
            if (params.containsKey("district")) {
                builder.queryParam("filters[District]", params.get("district"));
            }
            if (params.containsKey("commodity")) {
                builder.queryParam("filters[Commodity]", params.get("commodity"));
            }
            if (params.containsKey("limit")) {
                builder.queryParam("limit", params.get("limit"));
            } else {
                builder.queryParam("limit", 10);
            }

            String url = builder.toUriString();
            log.debug("Mandi API URL: {}", url);

            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return new ToolResult(response, true, "Success");

        } catch (Exception e) {
            log.error("Error fetching mandi prices", e);
            return new ToolResult(null, false, "Error: " + e.getMessage());
        }
    }
}
