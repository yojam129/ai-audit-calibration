CREATE TABLE detection_target_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    organization_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    result_label VARCHAR(20) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_fact_event_target (event_id, target_code),
    UNIQUE KEY uk_fact_order_target (organization_id, order_id, target_code),
    KEY idx_fact_rate_window (organization_id, target_code, occurred_at)
);

CREATE TABLE positive_rate_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    organization_id VARCHAR(64) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    window_start DATETIME(3) NOT NULL,
    window_end DATETIME(3) NOT NULL,
    numerator INT NOT NULL,
    denominator INT NOT NULL,
    positive_rate DECIMAL(10,6) NOT NULL,
    baseline_numerator INT NOT NULL,
    baseline_denominator INT NOT NULL,
    baseline_rate DECIMAL(10,6) NOT NULL,
    deviation DECIMAL(10,6) NOT NULL,
    level VARCHAR(4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rate_alert_window (organization_id, target_code, window_start, window_end)
);
