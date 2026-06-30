package org.kishorereddy.occasionpredictor.repository;

import org.kishorereddy.occasionpredictor.entity.ModelVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ModelVersionRepository extends JpaRepository<ModelVersion, UUID> {
    Optional<ModelVersion> findByActiveTrue();
}
