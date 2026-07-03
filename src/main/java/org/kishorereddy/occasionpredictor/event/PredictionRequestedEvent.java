package org.kishorereddy.occasionpredictor.event;

import java.time.Instant;

public record PredictionRequestedEvent(
        String eventId,
        String orderId,
        String recipientName,
        String recipientRelation,
        String productName,
        String productCategory,
        String orderDate,
        String giftMessage,
        Instant occurredAt
) {}
