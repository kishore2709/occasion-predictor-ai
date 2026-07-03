package org.kishorereddy.occasionpredictor.repository;

import org.kishorereddy.occasionpredictor.entity.SecurityAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SecurityAuditLogRepository extends JpaRepository<SecurityAuditLog, UUID> {
}
