package org.kishorereddy.occasionpredictor.model;

import jakarta.validation.constraints.NotBlank;

public record PredictionRequest(
        @NotBlank String orderId,
        String recipientName,
        String recipientRelation,
        String productName,
        String productCategory,
        String orderDate,
        String giftMessage
) {
}