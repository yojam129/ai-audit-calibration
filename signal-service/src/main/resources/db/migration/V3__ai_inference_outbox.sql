CREATE TABLE ai_inference_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    curve_id VARCHAR(64) NOT NULL,
    run_no VARCHAR(64) NOT NULL,
    chamber VARCHAR(8) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    judgement VARCHAR(24),
    confidence DECIMAL(8,6),
    evidence_json JSON,
    model_version VARCHAR(128),
    failure_reason VARCHAR(500),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_ai_curve(curve_id)
);

CREATE TABLE inference_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id CHAR(36) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_inference_event(event_id),
    KEY idx_inference_outbox(status, created_at)
);
