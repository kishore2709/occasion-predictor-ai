package org.kishorereddy.occasionpredictor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kishorereddy.occasionpredictor.model.OccasionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PredictionWorkflow {

    private static final Logger log = LoggerFactory.getLogger(PredictionWorkflow.class);
    private static final double CONFIDENCE_THRESHOLD = 0.4;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.chat.model:llama3}")
    private String model;

    @Value("${spring.ai.ollama.chat.options.temperature:0.1}")
    private double temperature;

    @Value("${spring.ai.ollama.chat.options.top-k:40}")
    private int topK;

    @Value("${spring.ai.ollama.chat.options.top-p:0.9}")
    private double topP;

    @Value("${spring.ai.ollama.chat.options.num-predict:200}")
    private int numPredict;

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
            return fallback(null, "Empty response from LLM");
        }

        return parseAndValidate(content.trim());
    }

    private LlmResult parseAndValidate(String content) {
        String cleaned = content.replaceAll("(?s)```(?:json)?\\s*", "").trim();

        JsonNode node;
        try {
            node = objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("LLM response is not valid JSON: {}", content);
            return fallback(content, "Response is not valid JSON");
        }

        // Validate occasion
        String occasionStr = node.path("occasion").asText("").trim().toUpperCase();
        if (occasionStr.isBlank()) {
            log.warn("Missing 'occasion' field in LLM response");
            return fallback(content, "Missing occasion field");
        }
        OccasionType occasion = toOccasionType(occasionStr);
        if (occasion == OccasionType.UNKNOWN && !occasionStr.equals("UNKNOWN")) {
            log.warn("Unrecognised occasion value '{}' in LLM response", occasionStr);
            return fallback(content, "Unrecognised occasion value: " + occasionStr);
        }

        // Validate confidence
        if (!node.has("confidence")) {
            log.warn("Missing 'confidence' field in LLM response");
            return fallback(content, "Missing confidence field");
        }
        double confidence = node.path("confidence").asDouble(-1);
        if (confidence < 0.0 || confidence > 1.0) {
            log.warn("Confidence {} is outside [0, 1]", confidence);
            return fallback(content, "Confidence out of range: " + confidence);
        }

        // Enforce confidence threshold
        if (confidence < CONFIDENCE_THRESHOLD && occasion != OccasionType.UNKNOWN) {
            log.info("Confidence {:.2f} below threshold {:.2f} — downgrading to UNKNOWN", confidence, CONFIDENCE_THRESHOLD);
            occasion = OccasionType.UNKNOWN;
        }

        // Extract reason (required)
        String reason = node.path("reason").asText("").trim();
        if (reason.isBlank()) {
            log.warn("Missing or empty 'reason' field in LLM response");
            reason = "No reason provided by model.";
        }

        // Extract evidence (optional array)
        List<String> evidence = new ArrayList<>();
        JsonNode evidenceNode = node.path("evidence");
        if (evidenceNode.isArray()) {
            for (JsonNode item : evidenceNode) {
                String s = item.asText("").trim();
                if (!s.isBlank()) evidence.add(s);
            }
        }

        return new LlmResult(occasion, confidence, reason, evidence, content, buildModelParameters());
    }

    private LlmResult fallback(String rawContent, String fallbackReason) {
        return new LlmResult(OccasionType.UNKNOWN, 0.0, fallbackReason, List.of(), rawContent, buildModelParameters());
    }

    private String buildModelParameters() {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "temperature", temperature,
                    "topK", topK,
                    "topP", topP,
                    "numPredict", numPredict
            ));
        } catch (Exception e) {
            return "{}";
        }
    }

    private OccasionType toOccasionType(String value) {
        try {
            return OccasionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return OccasionType.UNKNOWN;
        }
    }

    public record LlmResult(
            OccasionType occasion,
            double confidence,
            String reason,
            List<String> evidence,
            String rawContent,
            String modelParameters
    ) {}
}
