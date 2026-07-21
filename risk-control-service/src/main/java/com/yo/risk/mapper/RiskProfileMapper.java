package com.yo.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.risk.domain.po.RiskProfile;
import com.yo.risk.domain.vo.RiskTotalsRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RiskProfileMapper extends BaseMapper<RiskProfile> {
  @Update("""
      UPDATE risk_profile
      SET reviewed = #{profile.reviewed}, correct_count = #{profile.correctCount},
          total_duration_ms = #{profile.totalDurationMs}, error_counts_json = #{profile.errorCountsJson},
          level = #{profile.level}, training_required = #{profile.trainingRequired}, version = version + 1
      WHERE id = #{profile.id} AND version = #{profile.version}
      """)
  int updateProfile(@Param("profile") RiskProfile profile);

  @Select("""
      SELECT COALESCE(SUM(reviewed), 0) AS reviewed,
             COALESCE(SUM(correct_count), 0) AS correct,
             COALESCE(SUM(total_duration_ms), 0) AS total_duration_ms
      FROM risk_profile
      WHERE reviewer_id = #{reviewerId}
      """)
  RiskTotalsRow totals(@Param("reviewerId") String reviewerId);
}
