CREATE TABLE reviewer_qualification_state (
    reviewer_id VARCHAR(64) PRIMARY KEY,
    auth_user_id BIGINT NULL,
    recent_reviewed INT NOT NULL DEFAULT 0,
    recent_correct_count INT NOT NULL DEFAULT 0,
    recent_results_json JSON NOT NULL,
    training_required BOOLEAN NOT NULL DEFAULT FALSE,
    reset_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0
);
