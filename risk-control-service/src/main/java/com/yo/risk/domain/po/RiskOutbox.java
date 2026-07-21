package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
import java.util.UUID;

@TableName("risk_outbox")
public class RiskOutbox {
  @TableId public UUID id;
  public String routingKey;
  public String payload;
  public String status;
  public int attempts;
  public Instant nextAttemptAt;
  public Instant createdAt;
  public Instant publishedAt;
}
