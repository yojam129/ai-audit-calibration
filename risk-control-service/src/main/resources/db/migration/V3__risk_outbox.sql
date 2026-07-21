CREATE TABLE risk_outbox(
 id BINARY(16) PRIMARY KEY,routing_key VARCHAR(128) NOT NULL,payload JSON NOT NULL,
 status VARCHAR(16) NOT NULL,attempts INT NOT NULL DEFAULT 0,next_attempt_at TIMESTAMP(6) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL,published_at TIMESTAMP(6),KEY idx_risk_outbox(status,next_attempt_at)
);
