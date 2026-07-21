package com.yo.notification.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("notification_preference")
public class NotificationPreference {
  @TableId public String userId;
  public String email;
  public boolean emailEnabled;
  public boolean inAppEnabled;
  public String eventTypes;
  public Instant updatedAt;
}
