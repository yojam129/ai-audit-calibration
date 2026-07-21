package com.yo.alert.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.util.*;
import lombok.Data;

@TableName("alert")
@Data
public class AlertPO {
  @TableId public UUID id;
  public UUID sampleId;
  public long comparisonVersion;
  public String level;
  public String status;
  public String reasonCodes;
  public String alertLogic;
  public Instant slaDueAt;
  public String ownerId;
  public String processInstanceId;
  public String flowableTaskId;
  @Version public long version;
}
