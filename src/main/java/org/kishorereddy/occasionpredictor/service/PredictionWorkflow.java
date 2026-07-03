package org.kishorereddy.occasionpredictor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.model.OccasionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class PredictionWorkflow {

    private static final Logger log = LoggerFactory.getLogger(PredictionWorkflow.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public PredictionWorkflow(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    public LlmResult call(String prompt) {
        String content;
        try {
            content = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }

        if (content == null || content.isBlank()) {
            log.warn("Received empty response from LLM");
            return new LlmResult(OccasionType.UNKNOWN, 0.5, content);
        }

        return parseResponse(content.trim());
    }

    private LlmResult parseResponse(String content) {
        // Strip markdown code fences if the model wraps its JSON
        String cleaned = content.replaceAll("(?s)```(?:json)?\\s*", "").trim();

        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String occasionStr = node.path("occasion").asText("").trim().toUpperCase();
            double confidence = node.path("confidence").asDouble(0.85);
            OccasionType occasion = toOccasionType(occasionStr);
            return new LlmResult(occasion, confidence, content);
        } catch (Exception e) {
            log.debug("Response is not valid JSON, scanning for occasion token in: {}", content);
        }

        // Fallback: scan raw text for a known enum token (skip UNKNOWN to avoid false positives)
        String upper = content.toUpperCase();
        for (OccasionType type : OccasionType.values()) {
            if (type != OccasionType.UNKNOWN && upper.contains(type.name())) {
                return new LlmResult(type, 0.7, content);
            }
        }

        log.warn("Could not determine occasion from LLM response: {}", content);
        return new LlmResult(OccasionType.UNKNOWN, 0.5, content);
    }

    private OccasionType toOccasionType(String value) {
        try {
            return OccasionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return OccasionType.UNKNOWN;
        }
    }

    public record LlmResult(OccasionType occasion, double confidence, String rawContent) {}
}
