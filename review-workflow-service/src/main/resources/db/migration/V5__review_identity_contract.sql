ALTER TABLE review_task
    ADD COLUMN owner_auth_user_id BIGINT,
    ADD COLUMN claimed_at TIMESTAMP(6);
ALTER TABLE ground_truth
    ADD COLUMN auth_user_id BIGINT,
    ADD COLUMN duration_ms BIGINT;
CREATE INDEX idx_review_owner_auth ON review_task(owner_auth_user_id, status);
