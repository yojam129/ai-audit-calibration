ALTER TABLE alert
    ADD COLUMN process_instance_id VARCHAR(64) NULL AFTER owner_id,
    ADD COLUMN flowable_task_id VARCHAR(64) NULL AFTER process_instance_id,
    ADD KEY idx_alert_workflow_link (status, process_instance_id);
