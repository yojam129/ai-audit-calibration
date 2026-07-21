package com.yo.sample.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PrimaryReviewTaskVO(
    Long id,
    Long sampleId,
    String sampleBusinessId,
    String sampleNo,
    String runNo,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    String status,
    String reviewerName,
    long version,
    LocalDateTime createdAt,
    List<TargetEvidence> targets) {
  public record TargetEvidence(
      String chamber,
      String channelCode,
      String targetCode,
      String systemLabel,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      List<String> riskFlags,
      String aiLabel,
      Double aiConfidence,
      String aiEvidenceJson,
      String aiStatus) {}
}
