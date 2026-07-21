DROP INDEX idx_outcome_archived_truth ON ground_truth_outcome_fact;

CREATE INDEX idx_outcome_archived_time
    ON ground_truth_outcome_fact(archived, occurred_at);
