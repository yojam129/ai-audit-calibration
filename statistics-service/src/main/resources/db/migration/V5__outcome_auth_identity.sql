ALTER TABLE ground_truth_outcome_fact
    ADD COLUMN auth_user_id BIGINT AFTER reviewer_id;
CREATE INDEX idx_outcome_auth_user_time
    ON ground_truth_outcome_fact(auth_user_id, occurred_at);
