ALTER TABLE review_task
    ADD COLUMN run_no VARCHAR(128) NULL AFTER sample_id,
    ADD COLUMN primary_task_id BIGINT NULL AFTER run_no;

CREATE INDEX idx_review_process_instance ON review_task(process_instance_id);
CREATE INDEX idx_review_flow_state ON review_task(status, created_at);
