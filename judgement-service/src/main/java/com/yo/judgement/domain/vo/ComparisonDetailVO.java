package com.yo.judgement.domain.vo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class ComparisonDetailVO {
  private UUID id;
  private UUID sampleId;
  private String sampleNo;
  private long comparisonVersion;
  private String consistency;
  private int riskRank;
  private List<String> reasonCodes;
  private Instant createdAt;
  private List<TargetVO> targets;

  @Data
  public static class TargetVO {
    private String targetCode;
    private String systemLabel;
    private String primaryLabel;
    private String aiLabel;
    private String consistency;
    private String dissentingSource;
    private int riskRank;
    private List<String> reasonCodes;
  }
}
