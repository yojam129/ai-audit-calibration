package com.yo.judgement.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.util.*;

@TableName("comparison_run")
public class ComparisonRunPO {
  @TableId public UUID id;
  public UUID sampleId;
  public long comparisonVersion;
  public String consistency;
  public int riskRank;
  public String reasonCodes;
  public String targetsJson;
  public Instant createdAt;

  public UUID getSampleId() {
    return sampleId;
  }

  public long getComparisonVersion() {
    return comparisonVersion;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
