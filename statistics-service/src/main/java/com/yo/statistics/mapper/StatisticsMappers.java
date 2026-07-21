package com.yo.statistics.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.statistics.domain.po.StatisticsPO.Accuracy;
import com.yo.statistics.domain.po.StatisticsPO.Confusion;
import com.yo.statistics.domain.po.StatisticsPO.DailyAccuracy;
import com.yo.statistics.domain.po.StatisticsPO.Event;
import com.yo.statistics.domain.po.StatisticsPO.OutcomeFact;
import com.yo.statistics.domain.vo.StatisticsVO.InconsistencyDetail;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public final class StatisticsMappers {
  private StatisticsMappers() {}

  @Mapper
  public interface AccuracyMapper extends BaseMapper<Accuracy> {
    @Insert(
        "INSERT INTO accuracy_projection(source_type,correct_count,total_count,updated_at) VALUES(#{source},#{correct},1,#{now}) ON DUPLICATE KEY UPDATE correct_count=correct_count+VALUES(correct_count),total_count=total_count+1,updated_at=VALUES(updated_at)")
    int increment(
        @Param("source") String source, @Param("correct") int correct, @Param("now") Instant now);
  }

  @Mapper
  public interface ConfusionMapper extends BaseMapper<Confusion> {
    @Insert(
        "INSERT INTO confusion_projection(projection_key,source_type,target_code,tp,tn,fp,fn,indeterminate,invalid_count,updated_at) VALUES(#{key},#{source},#{target},#{tp},#{tn},#{fp},#{fn},#{indeterminate},#{invalid},#{now}) ON DUPLICATE KEY UPDATE tp=tp+VALUES(tp),tn=tn+VALUES(tn),fp=fp+VALUES(fp),fn=fn+VALUES(fn),indeterminate=indeterminate+VALUES(indeterminate),invalid_count=invalid_count+VALUES(invalid_count),updated_at=VALUES(updated_at)")
    int increment(
        @Param("key") String key,
        @Param("source") String source,
        @Param("target") String target,
        @Param("tp") int tp,
        @Param("tn") int tn,
        @Param("fp") int fp,
        @Param("fn") int fn,
        @Param("indeterminate") int indeterminate,
        @Param("invalid") int invalid,
        @Param("now") Instant now);
  }

  @Mapper
  public interface EventMapper extends BaseMapper<Event> {}

  @Mapper
  public interface DailyAccuracyMapper extends BaseMapper<DailyAccuracy> {
    @Insert(
        "INSERT INTO daily_accuracy_projection(projection_key,metric_date,source_type,correct_count,total_count,updated_at) VALUES(#{key},#{date},#{source},#{correct},1,#{now}) ON DUPLICATE KEY UPDATE correct_count=correct_count+VALUES(correct_count),total_count=total_count+1,updated_at=VALUES(updated_at)")
    int increment(
        @Param("key") String key,
        @Param("date") LocalDate date,
        @Param("source") String source,
        @Param("correct") int correct,
        @Param("now") Instant now);
  }

  @Mapper
  public interface OutcomeFactMapper extends BaseMapper<OutcomeFact> {
    @Select("""
        SELECT BIN_TO_UUID(sample_id) AS sample_id,
               truth_version,
               target_code,
               MAX(truth_label) AS truth_label,
               MAX(instrument_conclusion) AS system_label,
               MAX(human_conclusion) AS primary_label,
               MAX(ai_conclusion) AS ai_label,
               CASE WHEN MAX(instrument_conclusion) IS NULL THEN NULL
                    ELSE MAX(instrument_conclusion) = MAX(truth_label) END AS system_correct,
               CASE WHEN MAX(human_conclusion) IS NULL THEN NULL
                    ELSE MAX(human_conclusion) = MAX(truth_label) END AS primary_correct,
               CASE WHEN MAX(ai_conclusion) IS NULL THEN NULL
                    ELSE MAX(ai_conclusion) = MAX(truth_label) END AS ai_correct,
               MAX(occurred_at) AS occurred_at
        FROM ground_truth_outcome_fact
        WHERE source_type IN ('SYSTEM', 'PRIMARY', 'AI')
          AND archived = 1
        GROUP BY event_id, sample_id, truth_version, target_code
        HAVING (MAX(instrument_conclusion) IS NOT NULL
                    AND MAX(instrument_conclusion) <> MAX(truth_label))
            OR (MAX(human_conclusion) IS NOT NULL
                    AND MAX(human_conclusion) <> MAX(truth_label))
            OR (MAX(ai_conclusion) IS NOT NULL
                    AND MAX(ai_conclusion) <> MAX(truth_label))
        ORDER BY occurred_at DESC, sample_id, target_code
        """)
    IPage<InconsistencyDetail> selectInconsistencyDetails(Page<?> page);
  }
}
