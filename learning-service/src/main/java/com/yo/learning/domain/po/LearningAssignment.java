package com.yo.learning.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
import lombok.Data;

@TableName("learning_assignment")
@Data
public class LearningAssignment {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String reviewerId;
  public Long authUserId;
  public String courseCode;
  public String errorType;
  public String focusSampleId;
  public String focusSampleNo;
  public String focusChamber;
  public String focusChannelCode;
  public String focusTargetCode;
  public String status;
  public int attempts;
  public double bestScore;
  public Instant dueAt;
  public Instant appliedAt;
  public Long approvedByAuthUserId;
  public String processInstanceId;
  public String workflowToken;
  public Instant workflowStartedAt;
  @Version public long version;
}
