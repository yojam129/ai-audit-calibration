package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("risk_consumed_event")
public class RiskConsumedEvent {
  @TableId
  private String eventId;

  private Instant consumedAt;
}
