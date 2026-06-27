package org.kishorereddy.occasionpredictor.service.impl;

import org.kishorereddy.occasionpredictor.model.OccasionType;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

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

        var llmOutput = chatClient
                .prompt(prompt)
                .call()
                .content();

        return new PredictionResponse(
                request.orderId(),
                OccasionType.UNKNOWN,
                0.5,
                llmOutput,
                "OLLAMA_CHAT_CLIENT"
        );
    }
}
