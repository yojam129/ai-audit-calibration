ALTER TABLE sys_user
    ADD COLUMN email VARCHAR(320) NULL AFTER display_name,
    ADD COLUMN mobile VARCHAR(32) NULL AFTER email,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER mobile,
    ADD COLUMN last_login_at DATETIME(3) NULL AFTER token_version,
    ADD COLUMN created_by BIGINT NULL AFTER last_login_at,
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER created_by,
    ADD COLUMN updated_by BIGINT NULL AFTER created_at,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER updated_by,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at,
    ADD UNIQUE KEY uk_user_email (email),
    ADD KEY idx_user_org_status (org_id, status, deleted);

ALTER TABLE sys_role
    ADD COLUMN description VARCHAR(255) NULL AFTER role_name,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER description,
    ADD COLUMN data_scope VARCHAR(20) NOT NULL DEFAULT 'SELF' AFTER status,
    ADD COLUMN created_by BIGINT NULL AFTER data_scope,
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER created_by,
    ADD COLUMN updated_by BIGINT NULL AFTER created_at,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER updated_by,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at,
    ADD KEY idx_role_status (status, deleted);

ALTER TABLE sys_permission
    ADD COLUMN parent_id BIGINT NULL AFTER id,
    ADD COLUMN permission_type VARCHAR(20) NOT NULL DEFAULT 'API' AFTER permission_name,
    ADD COLUMN route_path VARCHAR(255) NULL AFTER permission_type,
    ADD COLUMN http_method VARCHAR(12) NULL AFTER route_path,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER http_method,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER sort_order,
    ADD COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER status,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE AFTER updated_at,
    ADD KEY idx_permission_parent_sort (parent_id, sort_order),
    ADD KEY idx_permission_status (status, deleted),
    ADD CONSTRAINT fk_permission_parent FOREIGN KEY (parent_id)
        REFERENCES sys_permission (id) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE sys_user_role
    ADD COLUMN granted_by BIGINT NULL AFTER role_id,
    ADD COLUMN granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER granted_by,
    ADD COLUMN expires_at DATETIME(3) NULL AFTER granted_at,
    ADD KEY idx_user_role_role (role_id, user_id),
    ADD KEY idx_user_role_expiry (expires_at),
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id)
        REFERENCES sys_user (id) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_user_role_role FOREIGN KEY (role_id)
        REFERENCES sys_role (id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE sys_role_permission
    ADD COLUMN granted_by BIGINT NULL AFTER permission_id,
    ADD COLUMN granted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER granted_by,
    ADD KEY idx_role_permission_permission (permission_id, role_id),
    ADD CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id)
        REFERENCES sys_role (id) ON DELETE CASCADE ON UPDATE CASCADE,
    ADD CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id)
        REFERENCES sys_permission (id) ON DELETE CASCADE ON UPDATE CASCADE;
