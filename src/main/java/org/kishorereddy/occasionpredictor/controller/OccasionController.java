package org.kishorereddy.occasionpredictor.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;
import org.kishorereddy.occasionpredictor.service.PredictionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/occasion")
@Tag(
        name = "Occasion Prediction",
        description = "APIs for predicting gift reminder occasions"
)
public class OccasionController {

    private final PredictionService predictionService;

    public OccasionController(PredictionService predictionService) {
        this.predictionService = predictionService;
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
    public PredictionResponse makePrediction(@RequestBody PredictionRequest predictionRequest) {
        return predictionService.predict(predictionRequest);
    }

    @GetMapping("/predictions/{id}")
    @Operation(
            summary = "Get prediction by ID",
            description = "Retrieves a specific prediction by its ID."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prediction retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Prediction not found")
    })
    public String getPrediction(@PathVariable String id) {
        return "Test prediction "+id;
    }
}
