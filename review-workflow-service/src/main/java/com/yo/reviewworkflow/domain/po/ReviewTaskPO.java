package com.yo.reviewworkflow.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
import java.util.*;
import lombok.Data;

@TableName("review_task")
@Data
public class ReviewTaskPO {
  @TableId public UUID id;
  public UUID sampleId;
  public String runNo;
  public Long primaryTaskId;
  public String primaryReviewerId;
  public Long primaryAuthUserId;
  public long primaryDurationMs;
  public String status;
  public String ownerId;
  public Long ownerAuthUserId;
  public Instant claimedAt;
  public String processInstanceId;
  public String flowableTaskId;
  public String priority;
  public String consistency;
  public String sourceTargetsJson;
  public Instant createdAt;
  public Instant slaDueAt;
  public Instant archivedAt;
  public long version;
}
