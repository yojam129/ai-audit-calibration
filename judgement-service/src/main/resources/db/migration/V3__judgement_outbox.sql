CREATE TABLE judgement_outbox (
    id BINARY(16) PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    last_error VARCHAR(1000) NULL,
    KEY idx_outbox_relay (status, next_attempt_at, created_at),
    UNIQUE KEY uk_outbox_event (aggregate_id, event_type)
);
