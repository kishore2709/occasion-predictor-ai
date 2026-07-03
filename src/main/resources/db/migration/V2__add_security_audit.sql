CREATE TABLE security_audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    principal   VARCHAR(255) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    roles       TEXT,
    ip_address  VARCHAR(100),
    occurred_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sec_audit_principal    ON security_audit_log (principal);
CREATE INDEX idx_sec_audit_occurred_at  ON security_audit_log (occurred_at);
CREATE INDEX idx_sec_audit_action       ON security_audit_log (action);
