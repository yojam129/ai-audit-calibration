package com.yo.statistics.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StatisticsRebuildMapper {
  @Delete("DELETE FROM accuracy_projection")
  void clearAccuracy();

  @Delete("DELETE FROM confusion_projection")
  void clearConfusion();

  @Delete("DELETE FROM daily_accuracy_projection")
  void clearDaily();

  @Insert(
      """
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
        AND archived = 1
      GROUP BY source_type
      """)
  void rebuildAccuracy();

  @Insert(
      """
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
            AND archived = 1
      ) facts GROUP BY source_type, target_code
      """)
  void rebuildConfusion();

  @Insert(
      """
      INSERT INTO daily_accuracy_projection(
          projection_key, metric_date, source_type, correct_count, total_count, updated_at)
      SELECT CONCAT(DATE(occurred_at), '|', source_type), DATE(occurred_at), source_type,
             SUM(CASE WHEN
               CASE source_type
                 WHEN 'SYSTEM' THEN instrument_conclusion
                 WHEN 'AI' THEN ai_conclusion
                 WHEN 'PRIMARY' THEN human_conclusion
               END = truth_label THEN 1 ELSE 0 END),
             COUNT(*), CURRENT_TIMESTAMP(6)
      FROM ground_truth_outcome_fact
      WHERE source_type IN ('SYSTEM', 'PRIMARY', 'AI')
        AND archived = 1
      GROUP BY DATE(occurred_at), source_type
      """)
  void rebuildDaily();
}
