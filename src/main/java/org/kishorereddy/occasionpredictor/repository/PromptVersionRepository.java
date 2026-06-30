package org.kishorereddy.occasionpredictor.repository;

import org.kishorereddy.occasionpredictor.entity.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, UUID> {
    Optional<PromptVersion> findByActiveTrue();
}
