CREATE TABLE sys_user_permission_override (
 user_id BIGINT NOT NULL, permission_code VARCHAR(128) NOT NULL,
 disabled BOOLEAN NOT NULL, reason VARCHAR(255) NOT NULL, updated_by BIGINT NOT NULL,
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 PRIMARY KEY(user_id,permission_code));
CREATE TABLE auth_operation_event (
 event_id VARCHAR(80) PRIMARY KEY, user_id BIGINT NOT NULL, operation VARCHAR(24) NOT NULL,
 created_at DATETIME(3) NOT NULL);
