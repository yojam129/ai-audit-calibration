package com.yo.reviewworkflow.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.util.*;
import lombok.Data;

@TableName("ground_truth")
@Data
public class GroundTruthPO {
  @TableId public UUID id;
  public UUID sampleId;
  public long truthVersion;
  public UUID taskId;
  public String reviewerId;
  public Long authUserId;
  public Long durationMs;
  public String targetsJson;
  public Instant confirmedAt;
}
