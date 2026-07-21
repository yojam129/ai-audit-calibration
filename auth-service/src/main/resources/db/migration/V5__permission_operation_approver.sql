ALTER TABLE auth_operation_event
    ADD COLUMN approved_by_auth_user_id BIGINT NULL AFTER operation,
    ADD COLUMN reason VARCHAR(255) NOT NULL DEFAULT '' AFTER approved_by_auth_user_id;
