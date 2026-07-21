package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("risk_policy")
public class RiskPolicy {
  @TableId(type = IdType.INPUT)
  private Long id;
  private double qualificationAccuracyThreshold;
  private Long updatedBy;
  private Instant updatedAt;
}
