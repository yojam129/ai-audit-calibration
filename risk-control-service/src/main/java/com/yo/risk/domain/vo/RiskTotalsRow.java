package com.yo.risk.domain.vo;

import lombok.Data;

@Data
public class RiskTotalsRow {
  private long reviewed;
  private long correct;
  private long totalDurationMs;
}
