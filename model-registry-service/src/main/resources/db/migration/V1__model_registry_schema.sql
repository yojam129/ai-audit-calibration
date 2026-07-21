CREATE TABLE model_version (
    id BIGINT PRIMARY KEY,
    model_code VARCHAR(80) NOT NULL,
    version VARCHAR(40) NOT NULL,
    runtime VARCHAR(40) NOT NULL,
    artifact_uri VARCHAR(512) NOT NULL,
    checksum CHAR(64) NOT NULL,
    metrics_json JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    traffic_percent INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_model_code_version (model_code, version),
    KEY idx_model_status_traffic (status, traffic_percent),
    CONSTRAINT ck_model_traffic_percent CHECK (traffic_percent BETWEEN 0 AND 100)
);
