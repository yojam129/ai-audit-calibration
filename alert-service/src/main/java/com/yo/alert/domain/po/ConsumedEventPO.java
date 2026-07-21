package com.yo.alert.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.util.*;

@TableName("alert_consumed_event")
public class ConsumedEventPO {
  @TableId public UUID eventId;
  public Instant consumedAt;
}
