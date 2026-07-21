RENAME TABLE consumed_event TO statistics_consumed_event;

INSERT INTO accuracy_projection
    (source_type, correct_count, total_count, updated_at)
VALUES
    ('AI', 936, 1000, CURRENT_TIMESTAMP(6)),
    ('HUMAN', 912, 1000, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    correct_count = VALUES(correct_count),
    total_count = VALUES(total_count),
    updated_at = VALUES(updated_at);

INSERT INTO confusion_projection
    (projection_key, source_type, target_code, tp, tn, fp, fn,
     indeterminate, invalid_count, updated_at)
VALUES
    ('AI|TARGET-A', 'AI', 'TARGET-A', 486, 450, 28, 36, 8, 2, CURRENT_TIMESTAMP(6)),
    ('HUMAN|TARGET-A', 'HUMAN', 'TARGET-A', 470, 442, 36, 52, 5, 1, CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE
    tp = VALUES(tp),
    tn = VALUES(tn),
    fp = VALUES(fp),
    fn = VALUES(fn),
    indeterminate = VALUES(indeterminate),
    invalid_count = VALUES(invalid_count),
    updated_at = VALUES(updated_at);
