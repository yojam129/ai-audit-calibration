package com.yo.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.learning.domain.po.LearningOutbox;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LearningOutboxMapper extends BaseMapper<LearningOutbox> {
  @Update(
      """
      UPDATE learning_outbox
      SET status='SENDING', next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
      WHERE id=#{id} AND status IN ('PENDING','RETRY')
        AND next_attempt_at<=CURRENT_TIMESTAMP(3)
      """)
  int claim(@Param("id") Long id);

  @Update(
      """
      UPDATE learning_outbox
      SET status='RETRY', last_error='recovered stale SENDING lease'
      WHERE status='SENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3)
      """)
  int recoverStale();
}
