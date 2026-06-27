package org.kishorereddy.occasionpredictor.service;

import org.kishorereddy.occasionpredictor.model.PredictionRequest;
import org.kishorereddy.occasionpredictor.model.PredictionResponse;

public interface PredictionService {
    PredictionResponse predict(PredictionRequest request);
}