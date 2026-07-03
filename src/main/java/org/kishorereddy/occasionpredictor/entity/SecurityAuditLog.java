package org.kishorereddy.occasionpredictor.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "security_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class SecurityAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String principal;

    @Column(nullable = false)
    private String action;

    private String resourceId;
    private String roles;
    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    void prePersist() {
        occurredAt = LocalDateTime.now();
    }
}
