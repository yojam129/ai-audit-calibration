package com.yo.signal.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("inference_outbox")
@Data
public class InferenceOutbox {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String eventId;
  public String aggregateId;
  public String eventType;
  public String payloadJson;
  public String status;
  public LocalDateTime createdAt;
  public int attempts;
  public LocalDateTime nextAttemptAt;
  public LocalDateTime publishedAt;
  public String lastError;
}
