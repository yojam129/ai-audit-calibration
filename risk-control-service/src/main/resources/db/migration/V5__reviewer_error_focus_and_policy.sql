CREATE TABLE risk_policy (
    id BIGINT PRIMARY KEY,
    qualification_accuracy_threshold DECIMAL(6,5) NOT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL
);

INSERT INTO risk_policy(id, qualification_accuracy_threshold, updated_by, updated_at)
VALUES (1, 0.10000, NULL, CURRENT_TIMESTAMP(3));

CREATE TABLE reviewer_error_focus (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(160) NOT NULL,
    reviewer_id VARCHAR(64) NOT NULL,
    auth_user_id BIGINT NOT NULL,
    sample_id CHAR(36) NULL,
    sample_no VARCHAR(64) NULL,
    chamber VARCHAR(8) NULL,
    channel_code VARCHAR(32) NULL,
    target_code VARCHAR(64) NOT NULL,
    predicted_label VARCHAR(32) NOT NULL,
    truth_label VARCHAR(32) NOT NULL,
    error_type VARCHAR(80) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_reviewer_error_event(event_id),
    KEY idx_reviewer_error_focus(reviewer_id, occurred_at),
    KEY idx_reviewer_error_target(target_code, chamber, channel_code)
);
