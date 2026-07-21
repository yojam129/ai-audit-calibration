INSERT INTO risk_profile
    (id, reviewer_id, window_start, reviewed, correct_count, total_duration_ms,
     error_counts_json, level, training_required, version)
VALUES
    (1, 'reviewer-demo-01', '2026-07-01 00:00:00.000000',
     120, 112, 5400000,
     JSON_OBJECT('FALSE_NEGATIVE', 5, 'FALSE_POSITIVE', 3),
     'WATCH', TRUE, 0),
    (2, 'reviewer-demo-02', '2026-07-01 00:00:00.000000',
     135, 132, 4860000,
     JSON_OBJECT('FALSE_NEGATIVE', 1, 'FALSE_POSITIVE', 2),
     'NORMAL', FALSE, 0)
ON DUPLICATE KEY UPDATE
    reviewed = VALUES(reviewed),
    correct_count = VALUES(correct_count),
    error_counts_json = VALUES(error_counts_json),
    level = VALUES(level),
    training_required = VALUES(training_required);
