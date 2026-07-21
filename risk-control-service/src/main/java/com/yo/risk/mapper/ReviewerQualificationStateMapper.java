package com.yo.risk.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.risk.domain.po.ReviewerQualificationState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewerQualificationStateMapper
    extends BaseMapper<ReviewerQualificationState> {
  @Update("""
      UPDATE reviewer_qualification_state
      SET auth_user_id = #{state.authUserId},
          recent_reviewed = #{state.recentReviewed},
          recent_correct_count = #{state.recentCorrectCount},
          recent_results_json = #{state.recentResultsJson},
          training_required = #{state.trainingRequired},
          reset_at = #{state.resetAt},
          version = version + 1
      WHERE reviewer_id = #{state.reviewerId} AND version = #{state.version}
      """)
  int updateState(@Param("state") ReviewerQualificationState state);
}
