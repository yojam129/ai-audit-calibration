ALTER TABLE learning_assignment
    ADD COLUMN auth_user_id BIGINT NULL AFTER reviewer_id,
    ADD COLUMN approved_by_auth_user_id BIGINT NULL;

ALTER TABLE learning_outbox
    ADD COLUMN attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at DATETIME(3) NULL,
    ADD COLUMN published_at DATETIME(3) NULL,
    ADD COLUMN last_error VARCHAR(1000) NULL;

UPDATE learning_outbox
SET status = CASE status WHEN 'NEW' THEN 'PENDING' ELSE status END,
    next_attempt_at = COALESCE(next_attempt_at, created_at);

CREATE INDEX idx_learning_outbox_relay
    ON learning_outbox(status, next_attempt_at, created_at);
