ALTER TABLE model_version
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    ADD COLUMN activated_at DATETIME(3) NULL;

CREATE TABLE model_lifecycle_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_version_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    traffic_percent INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    operator_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_model_audit_version_time (model_version_id, created_at)
);

UPDATE model_version
SET activated_at = CASE WHEN status = 'DEPLOYED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,
    status = CASE status WHEN 'DEPLOYED' THEN 'ACTIVE' ELSE status END;

-- V2 contained UI-only placeholder rows without real artifacts. Never expose them as trained models.
UPDATE model_version
SET status = 'REJECTED', traffic_percent = 0, activated_at = NULL
WHERE checksum IN (REPEAT('c', 64), REPEAT('d', 64));
