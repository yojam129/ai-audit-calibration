INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type,
     route_path, http_method, sort_order, status)
VALUES
    (13, NULL, 'operations:view', '运营管理', 'MENU', '/operations', NULL, 130, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    route_path = VALUES(route_path),
    http_method = VALUES(http_method),
    status = VALUES(status);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, granted_by)
SELECT id, 13, 1
FROM sys_role
WHERE role_code = 'SUPER_ADMIN';

UPDATE sys_user
SET token_version = token_version + 1
WHERE username = 'admin';
