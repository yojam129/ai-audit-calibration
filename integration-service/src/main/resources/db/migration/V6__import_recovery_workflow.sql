ALTER TABLE import_row_task
    ADD COLUMN process_instance_id VARCHAR(64) NULL AFTER last_error,
    ADD COLUMN flowable_task_id VARCHAR(64) NULL AFTER process_instance_id,
    ADD KEY idx_import_row_recovery (status, process_instance_id);

ALTER TABLE import_batch
    ADD COLUMN process_instance_id VARCHAR(64) NULL AFTER failure_reason,
    ADD COLUMN flowable_task_id VARCHAR(64) NULL AFTER process_instance_id,
    ADD KEY idx_import_batch_recovery (status, process_instance_id);
