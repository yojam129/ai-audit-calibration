package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("target_judgement")
@Data
public class TargetJudgement {
  @TableId(type = IdType.AUTO)
  public Long id;
  public Long runId;
  public String chamber;
  public String channelCode;
  public String targetCode;
  public String systemJudgement;
  public Double ctValue;
  public Double concentrationValue;
  public String concentrationUnit;
  public String riskLevel;
  public String riskFlags;
  public LocalDateTime createdAt;
}
