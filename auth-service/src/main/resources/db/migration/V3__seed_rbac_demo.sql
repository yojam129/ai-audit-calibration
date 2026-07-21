-- Demo credential: admin / Admin@123456
-- Change or disable this account before any non-demo deployment.
INSERT INTO sys_user
    (id, org_id, username, password_hash, display_name, email, status, enabled,
     token_version, created_by, updated_by)
VALUES
    (1, 1, 'admin',
     '$2a$12$pPRsTnYHO1RyiP1m1P4LPuuzVev7Nw6GPXGftKGVGX/GSofx5DRd.',
     '系统管理员', 'admin@example.invalid', 'ACTIVE', TRUE, 1, 1, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    status = VALUES(status),
    enabled = VALUES(enabled);

INSERT INTO sys_role
    (id, role_code, role_name, description, status, data_scope, created_by, updated_by)
VALUES
    (1, 'SUPER_ADMIN', '超级管理员', '演示环境全局管理角色', 'ACTIVE', 'ALL', 1, 1),
    (2, 'REVIEWER', '审核员', '执行样本判读与复核', 'ACTIVE', 'ORG', 1, 1),
    (3, 'QUALITY_MANAGER', '质量管理员', '查看统计并处置风险', 'ACTIVE', 'ORG', 1, 1)
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    description = VALUES(description),
    status = VALUES(status);

INSERT INTO sys_permission
    (id, parent_id, permission_code, permission_name, permission_type,
     route_path, http_method, sort_order, status)
VALUES
    (1, NULL, 'dashboard:view', '查看工作台', 'MENU', '/dashboard', NULL, 10, 'ACTIVE'),
    (2, NULL, 'sample:read', '查看样本', 'API', '/api/samples/**', 'GET', 20, 'ACTIVE'),
    (3, NULL, 'sample:import', '导入样本', 'API', '/api/integration/**', 'POST', 30, 'ACTIVE'),
    (4, NULL, 'judgement:submit', '提交判读', 'API', '/api/v1/comparisons/**', 'POST', 40, 'ACTIVE'),
    (5, NULL, 'alert:handle', '处置预警', 'API', '/api/v1/alerts/**', 'PATCH', 50, 'ACTIVE'),
    (6, NULL, 'review:handle', '执行复核', 'API', '/api/v1/reviews/**', 'POST', 60, 'ACTIVE'),
    (7, NULL, 'statistics:view', '查看统计', 'API', '/api/v1/statistics/**', 'GET', 70, 'ACTIVE'),
    (8, NULL, 'risk:manage', '风险管控', 'API', '/api/v1/risks/**', NULL, 80, 'ACTIVE'),
    (9, NULL, 'learning:participate', '参加培训考试', 'API', '/api/v1/learning/**', NULL, 90, 'ACTIVE'),
    (10, NULL, 'system:rbac', '用户角色权限管理', 'API', '/api/system/**', NULL, 100, 'ACTIVE'),
    (11, NULL, 'model:manage', '模型版本管理', 'API', '/api/models/**', NULL, 110, 'ACTIVE'),
    (12, NULL, 'trace:view', '查看审计追溯', 'API', '/api/v1/traces/**', 'GET', 120, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    route_path = VALUES(route_path),
    http_method = VALUES(http_method),
    status = VALUES(status);

INSERT IGNORE INTO sys_user_role (user_id, role_id, granted_by)
VALUES (1, 1, 1);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, granted_by)
SELECT 1, id, 1
FROM sys_permission
WHERE deleted = FALSE AND status = 'ACTIVE';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, granted_by)
VALUES
    (2, 1, 1), (2, 2, 1), (2, 4, 1), (2, 5, 1), (2, 6, 1), (2, 9, 1),
    (3, 1, 1), (3, 2, 1), (3, 5, 1), (3, 6, 1), (3, 7, 1),
    (3, 8, 1), (3, 12, 1);
