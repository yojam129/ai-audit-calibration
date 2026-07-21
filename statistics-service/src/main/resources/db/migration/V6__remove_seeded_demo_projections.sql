DELETE FROM accuracy_projection;

INSERT INTO accuracy_projection(source_type, correct_count, total_count, updated_at)
SELECT source_type,
       SUM(CASE WHEN
         CASE source_type
           WHEN 'SYSTEM' THEN instrument_conclusion
           WHEN 'AI' THEN ai_conclusion
           WHEN 'PRIMARY' THEN human_conclusion
         END = truth_label THEN 1 ELSE 0 END),
       COUNT(*), CURRENT_TIMESTAMP(6)
FROM ground_truth_outcome_fact
WHERE source_type IN ('SYSTEM', 'PRIMARY', 'AI')
GROUP BY source_type;

DELETE FROM confusion_projection;

INSERT INTO confusion_projection(
    projection_key, source_type, target_code, tp, tn, fp, fn,
    indeterminate, invalid_count, updated_at)
SELECT CONCAT(source_type, '|', target_code), source_type, target_code,
    SUM(predicted = 'POSITIVE' AND truth_label = 'POSITIVE'),
    SUM(predicted = 'NEGATIVE' AND truth_label = 'NEGATIVE'),
    SUM(predicted = 'POSITIVE' AND truth_label = 'NEGATIVE'),
    SUM(predicted = 'NEGATIVE' AND truth_label = 'POSITIVE'),
    SUM(predicted = 'INDETERMINATE'),
    SUM(predicted = 'INVALID'),
    CURRENT_TIMESTAMP(6)
FROM (
    SELECT source_type, target_code, truth_label,
           CASE source_type
             WHEN 'SYSTEM' THEN instrument_conclusion
             WHEN 'AI' THEN ai_conclusion
             WHEN 'PRIMARY' THEN human_conclusion
           END predicted
    FROM ground_truth_outcome_fact
    WHERE source_type IN ('SYSTEM', 'PRIMARY', 'AI')
) facts
GROUP BY source_type, target_code;

DELETE FROM daily_accuracy_projection;

INSERT INTO daily_accuracy_projection(
    projection_key, metric_date, source_type, correct_count, total_count, updated_at)
SELECT CONCAT(metric_date, '|', source_type), metric_date, source_type,
       SUM(predicted = truth_label),
       COUNT(*), CURRENT_TIMESTAMP(6)
FROM (
    SELECT DATE(occurred_at) metric_date, source_type, truth_label,
           CASE source_type
             WHEN 'SYSTEM' THEN instrument_conclusion
             WHEN 'AI' THEN ai_conclusion
             WHEN 'PRIMARY' THEN human_conclusion
           END predicted
    FROM ground_truth_outcome_fact
    WHERE source_type IN ('SYSTEM', 'PRIMARY', 'AI')
) facts
GROUP BY metric_date, source_type;
