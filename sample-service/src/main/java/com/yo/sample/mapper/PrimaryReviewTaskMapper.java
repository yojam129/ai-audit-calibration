package com.yo.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.sample.domain.po.PrimaryReviewTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;

@Mapper
public interface PrimaryReviewTaskMapper extends BaseMapper<PrimaryReviewTask> {
  @Update("""
      UPDATE primary_review_task
      SET status='IN_REVIEW', reviewer_auth_user_id=#{userId}, reviewer_name=#{username},
          claimed_at=#{claimedAt}, version=version+1
      WHERE id=#{id} AND status='PENDING' AND version=#{version}
      """)
  int claim(@Param("id") long id, @Param("version") long version,
      @Param("userId") long userId, @Param("username") String username,
      @Param("claimedAt") LocalDateTime claimedAt);

  @Update("""
      UPDATE primary_review_task
      SET status='SUBMITTED', targets_json=#{targetsJson}, submitted_at=#{submittedAt},
          duration_ms=#{durationMs}, version=version+1
      WHERE id=#{id} AND status='IN_REVIEW' AND version=#{version}
        AND reviewer_auth_user_id=#{userId}
      """)
  int submit(@Param("id") long id, @Param("version") long version,
      @Param("userId") long userId, @Param("targetsJson") String targetsJson,
      @Param("submittedAt") LocalDateTime submittedAt, @Param("durationMs") long durationMs);
}
