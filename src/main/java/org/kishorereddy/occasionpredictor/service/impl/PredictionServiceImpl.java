package org.kishorereddy.occasionpredictor.service.impl;

import org.kishorereddy.occasionpredictor.model.OccasionType;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class PredictionServiceImpl implements PredictionService {

    private final ChatClient chatClient;

    public PredictionServiceImpl(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public PredictionResponse predict(PredictionRequest request) {
        var prompt = """
                Predict the gift occasion.

                Return only one occasion from:
                BIRTHDAY, ANNIVERSARY, VALENTINES_DAY, MOTHERS_DAY,
                FATHERS_DAY, CHRISTMAS, THANKSGIVING, UNKNOWN.

                Recipient Name: %s
                Relation: %s
                Product: %s
                Category: %s
                Order Date: %s
                Gift Message: %s
                """.formatted(
                request.recipientName(),
                request.recipientRelation(),
                request.productName(),
                request.productCategory(),
                request.orderDate(),
                request.giftMessage()
        );

        var content = chatClient
                .prompt(prompt)
                .call()
                .content();

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Empty response from LLM");
        }

        var llmOutput = content.trim().toUpperCase();
        var prediction = parsePrediction(llmOutput, request);

        return new PredictionResponse(
                request.orderId(),
                prediction.occasion(),
                prediction.confidence(),
                prediction.reason(),
                "OLLAMA_CHAT_CLIENT"
        );
    }

    private record Prediction(OccasionType occasion, double confidence, String reason) {}

    private Prediction parsePrediction(String llmOutput, PredictionRequest request) {
        var occasion = parseOccasion(llmOutput);
        var confidence = calculateConfidence(occasion);
        var reason = generateReason(occasion, request);
        return new Prediction(occasion, confidence, reason);
    }

    private OccasionType parseOccasion(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return OccasionType.UNKNOWN;
        }
        try {
            return OccasionType.valueOf(llmOutput);
        } catch (IllegalArgumentException e) {
            return OccasionType.UNKNOWN;
        }
    }

    private String generateReason(OccasionType occasion, PredictionRequest request) {
        if (occasion == OccasionType.UNKNOWN) {
            return "Unable to determine a specific occasion for this gift.";
        }
        return String.format("Based on the %s (for %s), this gift is ideal for %s.",
                request.productName(),
                request.recipientRelation(),
                occasion.name().replace("_", " ").toLowerCase());
    }

    private double calculateConfidence(OccasionType occasion) {
        return occasion == OccasionType.UNKNOWN ? 0.5 : 0.85;
    }
}
