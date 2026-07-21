package com.yo.judgement.domain.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.UUID;

@TableName("judgement_outbox")
public class OutboxEventPO {
  @TableId public UUID id;
  public String aggregateType;
  public UUID aggregateId;
  public String eventType;
  public String routingKey;
  public String payload;
  public String status;
  public int attempts;
  public Instant nextAttemptAt;
  public Instant createdAt;
  public Instant publishedAt;
  public String lastError;
}
