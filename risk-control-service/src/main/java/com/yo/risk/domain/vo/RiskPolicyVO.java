package com.yo.risk.domain.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class RiskPolicyVO {
  private double qualificationAccuracyThreshold;
  private Long updatedBy;
  private Instant updatedAt;
}
