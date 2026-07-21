INSERT IGNORE INTO sys_role_permission (role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'learning:participate'
WHERE r.role_code = 'PRIMARY_REVIEWER';

UPDATE sys_user
SET token_version = token_version + 1
WHERE id IN (
    SELECT user_id
    FROM sys_user_role ur
    JOIN sys_role r ON r.id = ur.role_id
    WHERE r.role_code = 'PRIMARY_REVIEWER'
);
