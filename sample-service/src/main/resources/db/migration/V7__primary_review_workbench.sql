ALTER TABLE sample ADD COLUMN business_id CHAR(36) NULL AFTER id;
UPDATE sample SET business_id = UUID() WHERE business_id IS NULL;
ALTER TABLE sample MODIFY business_id CHAR(36) NOT NULL, ADD UNIQUE KEY uk_sample_business_id(business_id);

CREATE TABLE primary_review_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sample_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    reviewer_auth_user_id BIGINT NULL,
    reviewer_name VARCHAR(64) NULL,
    claimed_at DATETIME(3) NULL,
    submitted_at DATETIME(3) NULL,
    duration_ms BIGINT NULL,
    targets_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_primary_review_sample(sample_id),
    KEY idx_primary_review_status(status, created_at)
);

INSERT INTO primary_review_task(sample_id, run_id, status, created_at)
SELECT s.id, r.id, 'PENDING', s.created_at
FROM sample s
JOIN detection_order o ON o.sample_id=s.id
JOIN instrument_run r ON r.order_id=o.id
WHERE NOT EXISTS (SELECT 1 FROM primary_review_task t WHERE t.sample_id=s.id);
