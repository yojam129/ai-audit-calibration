package com.yo.judgement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yo.judgement.domain.po.OutboxEventPO;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEventPO> {
  @Update(
      "UPDATE judgement_outbox SET status='SENDING' WHERE id=#{id} AND status IN ('PENDING','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP(6)")
  int claim(@Param("id") UUID id);
}
