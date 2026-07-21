package com.yo.trace.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("trace_outbox")
public class TraceOutbox {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String eventId;
  public Long traceRecordId;
  public String status;
  public Integer attempts;
  public String lastError;
  public Instant nextAttemptAt;
  public Instant createdAt;
  public Instant publishedAt;
}
