package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("primary_review_task")
@Data
public class PrimaryReviewTask {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long sampleId;
  public Long runId;
  public String status;
  public Long reviewerAuthUserId;
  public String reviewerName;
  public LocalDateTime claimedAt;
  public LocalDateTime submittedAt;
  public Long durationMs;
  public String targetsJson;
  public LocalDateTime createdAt;
  public long version;
}
