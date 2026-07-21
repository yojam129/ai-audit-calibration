ALTER TABLE signal_index ADD COLUMN target_code VARCHAR(64) NULL AFTER channel_code;
UPDATE signal_index SET target_code=channel_code WHERE target_code IS NULL;
ALTER TABLE signal_index MODIFY target_code VARCHAR(64) NOT NULL;
ALTER TABLE inference_outbox
 ADD COLUMN attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN next_attempt_at DATETIME(3) NULL,
 ADD COLUMN published_at DATETIME(3) NULL,
 ADD COLUMN last_error VARCHAR(500) NULL;
UPDATE inference_outbox SET next_attempt_at=created_at WHERE next_attempt_at IS NULL;
CREATE INDEX idx_inference_relay ON inference_outbox(status,next_attempt_at);
