ALTER TABLE review_task
    ADD COLUMN flowable_task_id VARCHAR(64) NULL,
    ADD COLUMN priority VARCHAR(8) NOT NULL DEFAULT 'P2',
    ADD COLUMN consistency VARCHAR(32) NOT NULL DEFAULT 'TWO_AGREE_ONE_DIFF';

CREATE TABLE review_outbox (
    id BINARY(16) PRIMARY KEY,
    aggregate_id BINARY(16) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    KEY idx_review_outbox (status, next_attempt_at, created_at)
);
