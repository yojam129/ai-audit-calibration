ALTER TABLE import_row_task
    ADD COLUMN workflow_token CHAR(36) NULL AFTER flowable_task_id,
    ADD COLUMN recovery_resolution VARCHAR(16) NULL AFTER workflow_token,
    ADD COLUMN recovery_resolved_at DATETIME(3) NULL AFTER recovery_resolution,
    ADD UNIQUE KEY uk_import_row_workflow_token (workflow_token);

ALTER TABLE import_batch
    ADD COLUMN workflow_token CHAR(36) NULL AFTER flowable_task_id,
    ADD COLUMN recovery_resolution VARCHAR(16) NULL AFTER workflow_token,
    ADD COLUMN recovery_resolved_at DATETIME(3) NULL AFTER recovery_resolution,
    ADD UNIQUE KEY uk_import_batch_workflow_token (workflow_token);

CREATE TABLE import_recovery_decision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_token CHAR(36) NOT NULL,
    failure_scope VARCHAR(16) NOT NULL,
    subject_id BIGINT NOT NULL,
    resolution VARCHAR(16) NOT NULL,
    process_instance_id VARCHAR(64) NULL,
    resolved_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_import_recovery_decision_token (workflow_token),
    KEY idx_import_recovery_subject (failure_scope, subject_id, resolved_at)
);
