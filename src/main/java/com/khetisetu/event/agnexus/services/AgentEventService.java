package com.khetisetu.event.agnexus.services;

import com.khetisetu.event.agnexus.dto.ChatRequest;
import com.khetisetu.event.agnexus.dto.ChatResponse;
import com.khetisetu.event.agnexus.engine.AgentContext;
import com.khetisetu.event.agnexus.engine.AgentGraphEngine;
import com.khetisetu.event.agnexus.engine.AgentResponse;
import com.khetisetu.event.agnexus.memory.Conversation;
import com.khetisetu.event.agnexus.memory.ConversationMemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service that bridges Kafka events with the AI agent graph system.
 * Listens for agent queries on Kafka and publishes responses back.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentEventService {

    private final AgentGraphEngine engine;
    private final ConversationMemoryService memoryService;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    // Disabled by default: the async agnexus Kafka path (agnexus-queries/-responses)
    // is unused and the Aiven plan has no topic budget for it. Agent chat runs via
    // AgentController (REST). Set agnexus.kafka.enabled=true after upgrading the plan.
    @KafkaListener(topics = "agnexus-queries", groupId = "agnexus-group", containerFactory = "agnexusFactory",
            autoStartup = "${agnexus.kafka.enabled:false}")
    public void handleAgentQuery(String message) {
        log.info("Received agent query via Kafka: {}", message);
        try {
            JSONObject json = new JSONObject(message);
            String sessionId = json.optString("sessionId", "session-" + System.currentTimeMillis());
            String farmerId = json.optString("farmerId", "unknown");

            // Get or create conversation
            Conversation conversation = memoryService.getOrCreateConversation(farmerId, sessionId);

            AgentContext context = AgentContext.builder()
                    .userQuery(json.getString("query"))
                    .farmerId(farmerId)
                    .crop(json.optString("crop", "General"))
                    .location(json.optString("location", "Maharashtra"))
                    .sessionId(sessionId)
                    .conversationId(conversation.getId())
                    .language(json.optString("language", "en"))
                    .build();

            AgentResponse response = engine.executeGraph("RouterAgent", context);
            log.info("Agent execution complete. Terminal: {}, Agent: {}",
                    response.isTerminal(), response.getAgentName());

            // Send response back to Kafka
            Map<String, Object> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("conversationId", conversation.getId());
            result.put("farmerId", farmerId);
            result.put("response", response.getResponse());
            result.put("agentUsed", response.getAgentName());
            result.put("provider", response.getProvider());
            result.put("executionTimeMs", response.getExecutionTimeMs());
            result.put("data", response.getData());

            kafkaTemplate.send("agnexus-responses", sessionId, result);
            log.info("Sent agent response to Kafka for session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error processing agent query from Kafka", e);
        }
    }
}
