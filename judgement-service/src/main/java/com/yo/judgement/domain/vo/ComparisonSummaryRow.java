package com.yo.judgement.domain.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class ComparisonSummaryRow {
  private String id;
  private String sampleId;
  private long comparisonVersion;
  private String consistency;
  private int riskRank;
  private String reasonCodes;
  private String targetsJson;
  private Instant createdAt;
}
