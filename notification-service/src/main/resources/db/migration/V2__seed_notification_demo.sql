INSERT INTO notification_record
    (id, request_id, user_id, email, subject, body, status, read_flag,
     failure_reason, created_at, sent_at)
VALUES
    (1, 'NOTICE-DEMO-001', 'admin', 'admin@example.invalid',
     'P1 高风险预警', '演示样本存在 AI 与人工判读不一致，请及时复核。',
     'SENT', FALSE, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),
    (2, 'NOTICE-DEMO-002', 'reviewer-demo-01', 'reviewer@example.invalid',
     '培训任务提醒', '您有一项假阴性专项培训任务待完成。',
     'CREATED', FALSE, NULL, CURRENT_TIMESTAMP(6), NULL)
ON DUPLICATE KEY UPDATE
    subject = VALUES(subject),
    body = VALUES(body),
    status = VALUES(status),
    read_flag = VALUES(read_flag);
