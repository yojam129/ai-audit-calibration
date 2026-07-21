package com.yo.learning.domain.po;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("learning_outbox")
public class LearningOutbox {
  @TableId(type=IdType.AUTO) public Long id;
  public String eventId;
  public String eventType;
  public String aggregateId;
  public String payloadJson;
  public String status;
  public int attempts;
  public Instant nextAttemptAt;
  public Instant publishedAt;
  public String lastError;
  public Instant createdAt;
}
