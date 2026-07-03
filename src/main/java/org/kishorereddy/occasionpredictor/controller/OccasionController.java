package org.kishorereddy.occasionpredictor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.kishorereddy.occasionpredictor.entity.Prediction;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.repository.PredictionRepository;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/occasion")
@Tag(
        name = "Occasion Prediction",
        description = "APIs for predicting gift reminder occasions"
)
public class OccasionController {

    private final PredictionService predictionService;
    private final PredictionRepository predictionRepository;

    public OccasionController(PredictionService predictionService,
                              PredictionRepository predictionRepository) {
        this.predictionService = predictionService;
        this.predictionRepository = predictionRepository;
    }

    @GetMapping("/health")
    public String health() {
        return "Service is running!";
    }

    @PostMapping("/predictions")
    @Operation(
            summary = "Predict gift occasions",
            description = "Predicts possible gift occasions based on order items, recipients, brand, and country."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prediction completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid prediction request"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public PredictionResponse makePrediction(@Valid @RequestBody PredictionRequest predictionRequest) {
        return predictionService.predict(predictionRequest);
    }

    @GetMapping("/predictions/{id}")
    @Operation(
            summary = "Get prediction by ID",
            description = "Retrieves a specific prediction by its UUID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prediction retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Prediction not found")
    })
    public PredictionResponse getPrediction(@PathVariable UUID id) {
        Prediction p = predictionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prediction not found: " + id));

        return new PredictionResponse(
                p.getOrderId(),
                p.getPredictedOccasion(),
                p.getConfidenceScore(),
                p.getReason(),
                p.getPredictionSource(),
                p.getEvidence()
        );
    }
}
