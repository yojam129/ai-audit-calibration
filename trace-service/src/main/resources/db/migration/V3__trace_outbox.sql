CREATE TABLE trace_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(80) NOT NULL,
    trace_record_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500),
    next_attempt_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6),
    UNIQUE KEY uk_trace_outbox_event(event_id),
    KEY idx_trace_outbox_pending(status, next_attempt_at)
);
CREATE INDEX idx_trace_event_type_time ON trace_record(event_type, occurred_at);
