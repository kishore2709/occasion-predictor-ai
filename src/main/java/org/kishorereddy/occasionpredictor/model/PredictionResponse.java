package org.kishorereddy.occasionpredictor.model;

public record PredictionResponse(String orderId,
                                 OccasionType predictedOccasion,
                                 double confidenceScore,
                                 String reason,
                                 String predictionSource
) {
}