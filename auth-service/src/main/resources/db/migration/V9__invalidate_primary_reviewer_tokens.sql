UPDATE sys_user u
JOIN sys_user_role ur ON ur.user_id = u.id
JOIN sys_role r ON r.id = ur.role_id
SET u.token_version = u.token_version + 1,
    u.updated_at = CURRENT_TIMESTAMP(6)
WHERE r.role_code = 'PRIMARY_REVIEWER';
