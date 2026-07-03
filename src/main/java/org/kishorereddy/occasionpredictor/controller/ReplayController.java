package org.kishorereddy.occasionpredictor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kishorereddy.occasionpredictor.entity.Prediction;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.repository.PredictionRepository;
import org.kishorereddy.occasionpredictor.service.kafka.PredictionEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Admin endpoint to replay the {@code occasion.predicted} event for a stored prediction.
 * Use this when downstream consumers (reminder, notification, analytics) failed to
 * process an event and need to reprocess it without re-invoking the LLM.
 */
@RestController
@RequestMapping("/api/v1/occasion/admin")
@Tag(name = "Admin", description = "Admin operations for event replay and ops tooling")
public class ReplayController {

    private final PredictionRepository predictionRepository;
    private final PredictionEventPublisher eventPublisher;

    public ReplayController(PredictionRepository predictionRepository,
                            PredictionEventPublisher eventPublisher) {
        this.predictionRepository = predictionRepository;
        this.eventPublisher       = eventPublisher;
    }

    @PostMapping("/replay/{orderId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
            summary = "Replay occasion.predicted event",
            description = "Re-publishes the OccasionPredictedEvent for the most recent stored prediction "
                    + "of the given orderId. Triggers all downstream consumers (reminder, notification, "
                    + "analytics, feedback) without re-invoking the LLM."
    )
    public Map<String, String> replay(@PathVariable String orderId) {
        Prediction prediction = predictionRepository
                .findFirstByOrderIdOrderByCreatedAtDesc(orderId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No stored prediction for orderId: " + orderId));

        PredictionResponse response = new PredictionResponse(
                prediction.getOrderId(),
                prediction.getPredictedOccasion(),
                prediction.getConfidenceScore(),
                prediction.getReason(),
                prediction.getPredictionSource(),
                prediction.getEvidence()
        );

        eventPublisher.publishOccasionPredicted(prediction.getId().toString(), response);

        return Map.of(
                "status",  "accepted",
                "orderId", orderId,
                "message", "OccasionPredictedEvent replayed — all consumers will reprocess"
        );
    }
}
