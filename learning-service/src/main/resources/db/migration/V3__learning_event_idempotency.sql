ALTER TABLE learning_assignment ADD COLUMN external_event_id VARCHAR(128) NULL;
CREATE UNIQUE INDEX uk_learning_external_event ON learning_assignment(external_event_id);
