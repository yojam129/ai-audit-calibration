RENAME TABLE consumed_event TO alert_consumed_event;

INSERT INTO alert
    (id, sample_id, comparison_version, level, status, reason_codes,
     sla_due_at, owner_id, version)
VALUES
    (UNHEX(REPLACE('50000000-0000-0000-0000-000000000001', '-', '')),
     UNHEX(REPLACE('20000000-0000-0000-0000-000000000001', '-', '')),
     1, 'P1', 'OPEN', 'POSSIBLE_FALSE_NEGATIVE',
     TIMESTAMPADD(MINUTE, 15, CURRENT_TIMESTAMP(6)), NULL, 0)
ON DUPLICATE KEY UPDATE
    level = VALUES(level),
    status = VALUES(status),
    reason_codes = VALUES(reason_codes);
