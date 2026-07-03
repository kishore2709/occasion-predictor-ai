package org.kishorereddy.occasionpredictor.model;

import java.util.List;

public record PredictionResponse(
        String orderId,
        OccasionType predictedOccasion,
        double confidenceScore,
        String reason,
        String predictionSource,
        List<String> evidence
) {
}
