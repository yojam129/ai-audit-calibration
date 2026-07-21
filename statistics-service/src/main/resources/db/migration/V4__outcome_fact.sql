CREATE TABLE ground_truth_outcome_fact (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    sample_id BINARY(16) NOT NULL,
    truth_version BIGINT NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    instrument_conclusion VARCHAR(24),
    ai_conclusion VARCHAR(24),
    human_conclusion VARCHAR(24),
    truth_label VARCHAR(24) NOT NULL,
    reviewer_id VARCHAR(64),
    duration_ms BIGINT NOT NULL DEFAULT 0,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_outcome_event_source_target(event_id, source_type, target_code),
    KEY idx_outcome_sample_target(sample_id, target_code),
    KEY idx_outcome_time(occurred_at)
);
