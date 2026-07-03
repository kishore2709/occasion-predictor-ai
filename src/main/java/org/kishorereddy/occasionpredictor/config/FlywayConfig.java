package org.kishorereddy.occasionpredictor.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    /**
     * Repairs any previously-failed migrations (clears FAILED state from flyway_schema_history)
     * before applying pending ones. Safe for dev: lets the app self-recover when an external
     * dependency (e.g. pgvector extension) is added after a failed migration attempt.
     */
    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return flyway -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
