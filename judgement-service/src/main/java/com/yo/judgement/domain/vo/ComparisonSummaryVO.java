package com.yo.judgement.domain.vo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class ComparisonSummaryVO {
  private UUID id;
  private UUID sampleId;
  private String sampleNo;
  private long comparisonVersion;
  private String consistency;
  private int riskRank;
  private List<String> reasonCodes;
  private Instant createdAt;
}
