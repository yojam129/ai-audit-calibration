package com.yo.risk.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRiskPolicyDTO {
  @NotNull
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private Double qualificationAccuracyThreshold;
}
