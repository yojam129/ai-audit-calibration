CREATE TABLE import_row_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    row_no INT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    row_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3),
    last_error VARCHAR(500),
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_import_batch_row(batch_id, row_no),
    UNIQUE KEY uk_import_row_idempotency(idempotency_key),
    KEY idx_import_row_ready(status, next_attempt_at)
);
