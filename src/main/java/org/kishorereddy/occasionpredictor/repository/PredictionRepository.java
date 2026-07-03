package org.kishorereddy.occasionpredictor.repository;

import org.kishorereddy.occasionpredictor.entity.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    List<Prediction> findByOrderId(String orderId);

    java.util.Optional<Prediction> findFirstByOrderIdOrderByCreatedAtDesc(String orderId);
}
