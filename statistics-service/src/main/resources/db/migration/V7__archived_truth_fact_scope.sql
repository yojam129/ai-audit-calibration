ALTER TABLE ground_truth_outcome_fact
    ADD COLUMN archived TINYINT(1) NOT NULL DEFAULT 0 AFTER duration_ms,
    ADD COLUMN secondary_truth_confirmed TINYINT(1) NOT NULL DEFAULT 0 AFTER archived,
    ADD COLUMN archived_at TIMESTAMP(6) NULL AFTER secondary_truth_confirmed;

CREATE INDEX idx_outcome_archived_truth
    ON ground_truth_outcome_fact(archived, secondary_truth_confirmed, occurred_at);

DELETE FROM accuracy_projection;
DELETE FROM confusion_projection;
DELETE FROM daily_accuracy_projection;
