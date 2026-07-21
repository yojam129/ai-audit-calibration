ALTER TABLE notification_record
 ADD COLUMN attempts INT NOT NULL DEFAULT 0,
 ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
 ADD COLUMN event_type VARCHAR(80) NULL;
CREATE INDEX idx_notification_retry ON notification_record(status,next_attempt_at,attempts);
CREATE TABLE notification_preference(
 user_id VARCHAR(64) PRIMARY KEY,email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
 in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,updated_at TIMESTAMP(6) NOT NULL
);
