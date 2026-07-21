ALTER TABLE instrument_run ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE instrument_run ADD UNIQUE KEY uk_run_idempotency(idempotency_key);

CREATE TABLE target_judgement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    chamber VARCHAR(8) NOT NULL,
    target_code VARCHAR(64) NOT NULL,
    system_judgement VARCHAR(32) NOT NULL,
    ct_value DECIMAL(10,4),
    created_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_target_run(run_id, chamber, target_code)
);
