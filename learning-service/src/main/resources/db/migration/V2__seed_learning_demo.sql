INSERT INTO learning_assignment
    (id, reviewer_id, course_code, error_type, status, attempts,
     best_score, due_at, applied_at, version)
VALUES
    (1, 'reviewer-demo-01', 'COURSE-FN-001', 'FALSE_NEGATIVE',
     'ASSIGNED', 0, 0, TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(6)), NULL, 0),
    (2, 'reviewer-demo-02', 'COURSE-QC-001', 'QUALITY_CONTROL',
     'PASSED', 1, 92.50, TIMESTAMPADD(DAY, 7, CURRENT_TIMESTAMP(6)),
     CURRENT_TIMESTAMP(6), 0)
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    attempts = VALUES(attempts),
    best_score = VALUES(best_score);
