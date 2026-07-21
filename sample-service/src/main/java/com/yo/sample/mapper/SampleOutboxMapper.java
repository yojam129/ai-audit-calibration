package com.yo.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.sample.domain.po.SampleOutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SampleOutboxMapper extends BaseMapper<SampleOutboxEvent> {
  @Update(
      """
      UPDATE sample_outbox
      SET status='SENDING', next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 60 SECOND)
      WHERE id=#{id} AND status IN ('PENDING','RETRY')
        AND next_attempt_at<=CURRENT_TIMESTAMP(3)
      """)
  int claim(@Param("id") String id);

  @Update(
      """
      UPDATE sample_outbox
      SET status='RETRY', last_error='recovered stale SENDING lease'
      WHERE status='SENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3)
      """)
  int recoverStaleClaims();
}
