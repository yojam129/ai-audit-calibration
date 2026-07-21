package com.yo.judgement.domain.dto;

import com.yo.judgement.enums.JudgementEnums.Label;
import java.util.*;

public record ComparisonDTO(
    UUID sampleId,
    long comparisonVersion,
    String primaryReviewerId,
    Long primaryAuthUserId,
    long primaryDurationMs,
    List<TargetDTO> targets) {
  public record TargetDTO(
      String targetCode,
      Label systemLabel,
      Label primaryLabel,
      Label aiLabel,
      Double aiConfidence,
      boolean criticalTarget,
      boolean internalControl,
      boolean crossChannelRisk) {}
}
