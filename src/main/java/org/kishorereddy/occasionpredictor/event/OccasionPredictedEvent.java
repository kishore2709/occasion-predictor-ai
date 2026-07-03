package org.kishorereddy.occasionpredictor.event;

import java.time.Instant;
import java.util.List;

public record OccasionPredictedEvent(
        String eventId,
        String orderId,
        String predictionId,
        String occasion,
        double confidence,
        String reason,
        List<String> evidence,
        String predictionSource,
        Instant occurredAt
) {}
