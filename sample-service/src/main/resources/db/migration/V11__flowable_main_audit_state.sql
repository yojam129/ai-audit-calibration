UPDATE sample
SET status = 'PRIMARY_PENDING'
WHERE status = 'REVIEW_PENDING';

UPDATE primary_review_task
SET status = 'PENDING'
WHERE status IN ('WAITING_IMPORT', 'WAITING_AI');

CREATE INDEX idx_sample_flowable_state ON sample(status, id);
