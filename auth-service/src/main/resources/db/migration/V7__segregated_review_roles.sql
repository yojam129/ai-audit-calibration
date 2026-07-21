INSERT INTO sys_role
    (id, role_code, role_name, description, status, data_scope, created_by, updated_by)
VALUES
    (4, 'PRIMARY_REVIEWER', '一级审核员', '执行一级人工判读并参加风控培训考试', 'ACTIVE', 'ORG', 1, 1),
    (5, 'SECONDARY_REVIEWER', '二级审核员', '领取预警并完成二级终审', 'ACTIVE', 'ORG', 1, 1)
ON DUPLICATE KEY UPDATE role_name=VALUES(role_name), description=VALUES(description), status='ACTIVE';

INSERT INTO sys_user
    (id, org_id, username, password_hash, display_name, email, status, enabled,
     token_version, created_by, updated_by)
VALUES
    (2, 1, 'primary_reviewer', '$2a$12$pPRsTnYHO1RyiP1m1P4LPuuzVev7Nw6GPXGftKGVGX/GSofx5DRd.', '一级审核员', 'primary@example.invalid', 'ACTIVE', TRUE, 1, 1, 1),
    (3, 1, 'secondary_reviewer', '$2a$12$pPRsTnYHO1RyiP1m1P4LPuuzVev7Nw6GPXGftKGVGX/GSofx5DRd.', '二级审核员', 'secondary@example.invalid', 'ACTIVE', TRUE, 1, 1, 1),
    (4, 1, 'quality_manager', '$2a$12$pPRsTnYHO1RyiP1m1P4LPuuzVev7Nw6GPXGftKGVGX/GSofx5DRd.', '质量管理员', 'quality@example.invalid', 'ACTIVE', TRUE, 1, 1, 1)
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), status='ACTIVE', enabled=TRUE;

INSERT IGNORE INTO sys_user_role(user_id, role_id, granted_by) VALUES (2,4,1),(3,5,1),(4,3,1);
INSERT IGNORE INTO sys_role_permission(role_id, permission_id, granted_by)
SELECT 4, id, 1 FROM sys_permission WHERE permission_code IN
    ('dashboard:view','sample:read','judgement:submit','learning:participate');
INSERT IGNORE INTO sys_role_permission(role_id, permission_id, granted_by)
SELECT 5, id, 1 FROM sys_permission WHERE permission_code IN
    ('dashboard:view','sample:read','alert:handle','review:handle','trace:view');
