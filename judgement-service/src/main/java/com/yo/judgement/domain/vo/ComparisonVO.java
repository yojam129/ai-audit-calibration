package com.yo.judgement.domain.vo;

import com.yo.judgement.enums.JudgementEnums.*;
import java.util.*;

public record ComparisonVO(
    UUID sampleId,
    long comparisonVersion,
    String primaryReviewerId,
    Long primaryAuthUserId,
    long primaryDurationMs,
    Consistency consistency,
    int riskRank,
    List<String> reasonCodes,
    List<TargetVO> targets) {
  public record TargetVO(
      String targetCode,
      Label systemLabel,
      Label primaryLabel,
      Label aiLabel,
      Consistency consistency,
      String dissentingSource,
      int riskRank,
      List<String> reasonCodes) {}
}
