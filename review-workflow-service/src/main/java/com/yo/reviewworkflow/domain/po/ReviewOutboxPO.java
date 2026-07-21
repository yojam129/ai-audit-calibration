package com.yo.reviewworkflow.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
import java.util.UUID;

@TableName("review_outbox")
public class ReviewOutboxPO {
  @TableId public UUID id;
  public UUID aggregateId;
  public String routingKey;
  public String payload;
  public String status;
  public int attempts;
  public Instant nextAttemptAt;
  public Instant createdAt;
  public Instant publishedAt;
  public String lastError;
}
