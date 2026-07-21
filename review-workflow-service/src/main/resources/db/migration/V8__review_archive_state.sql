ALTER TABLE review_task
    ADD COLUMN archived_at TIMESTAMP(6) NULL AFTER sla_due_at;

UPDATE review_task
SET status = 'ARCHIVED',
    archived_at = COALESCE(archived_at, CURRENT_TIMESTAMP(6))
WHERE status IN ('FINALIZED', 'ARCHIVED');
