UPDATE sample
SET status = 'REVIEW_PENDING'
WHERE status = 'DETECTED';

CREATE INDEX idx_sample_review_status ON sample(status, created_at);
