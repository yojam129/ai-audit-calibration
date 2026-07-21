ALTER TABLE learning_assignment
    ADD COLUMN process_instance_id VARCHAR(64) NULL,
    ADD COLUMN workflow_token CHAR(36) NULL,
    ADD COLUMN workflow_started_at TIMESTAMP(6) NULL;

CREATE UNIQUE INDEX uk_learning_process_instance
    ON learning_assignment(process_instance_id);

CREATE UNIQUE INDEX uk_learning_workflow_token
    ON learning_assignment(workflow_token);
