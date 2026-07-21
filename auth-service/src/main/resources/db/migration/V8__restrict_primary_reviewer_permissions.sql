DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_code = 'PRIMARY_REVIEWER';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, granted_by)
SELECT r.id, p.id, 1
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'judgement:submit'
WHERE r.role_code = 'PRIMARY_REVIEWER';

DELETE upo
FROM sys_user_permission_override upo
JOIN sys_user_role ur ON ur.user_id = upo.user_id
JOIN sys_role r ON r.id = ur.role_id
WHERE r.role_code = 'PRIMARY_REVIEWER';

UPDATE sys_role
SET description = '仅执行一级人工判读，可在审核任务内查看检测曲线'
WHERE role_code = 'PRIMARY_REVIEWER';
