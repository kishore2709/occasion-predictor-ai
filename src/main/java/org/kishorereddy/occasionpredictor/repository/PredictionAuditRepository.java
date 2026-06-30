package org.kishorereddy.occasionpredictor.repository;

import org.kishorereddy.occasionpredictor.entity.PredictionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PredictionAuditRepository extends JpaRepository<PredictionAudit, UUID> {
    List<PredictionAudit> findByPredictionId(UUID predictionId);
}
