package com.yo.notification.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("notification_record")
public class NotificationRecord {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String requestId;
  public String userId;
  public String email;
  public String subject;
  public String body;
  public String status;
  public boolean readFlag;
  public String failureReason;
  public Instant createdAt;
  public Instant sentAt;
  public int attempts;
  public Instant nextAttemptAt;
  public String eventType;

  public String getRequestId() { return requestId; }
  public String getUserId() { return userId; }
  public String getStatus() { return status; }
  public boolean isReadFlag() { return readFlag; }
  public Instant getCreatedAt() { return createdAt; }
  public int getAttempts() { return attempts; }
  public Instant getNextAttemptAt() { return nextAttemptAt; }
}
