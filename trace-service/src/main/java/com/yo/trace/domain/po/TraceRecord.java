package com.yo.trace.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("trace_record")
public class TraceRecord {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String eventId;
  public String eventType;
  public String aggregateType;
  public String aggregateId;
  public String actorId;
  public String organizationId;
  public String traceId;
  public String payloadJson;
  public String previousHash;
  public String eventHash;
  public Instant occurredAt;
}
