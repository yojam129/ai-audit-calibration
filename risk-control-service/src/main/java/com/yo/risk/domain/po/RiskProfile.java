package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
import lombok.Data;

@TableName("risk_profile")
@Data
public class RiskProfile {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String reviewerId;
  public Instant windowStart;
  public long reviewed;
  public long correctCount;
  public long totalDurationMs;
  public String errorCountsJson;
  public String level;
  public boolean trainingRequired;
  @Version public long version;
}
