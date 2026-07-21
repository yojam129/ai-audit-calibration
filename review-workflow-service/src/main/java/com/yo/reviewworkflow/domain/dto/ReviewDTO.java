package com.yo.reviewworkflow.domain.dto;

import com.yo.reviewworkflow.enums.ReviewEnums.*;
import java.util.*;
import jakarta.validation.constraints.NotBlank;

public final class ReviewDTO {
  private ReviewDTO() {}

  public record Create(
      UUID sampleId,
      String primaryReviewerId,
      Long primaryAuthUserId,
      long primaryDurationMs,
      String priority,
      String consistency,
      boolean sampling,
      List<SourceTarget> sourceTargets) {}

  public record SourceTarget(
      String targetCode,
      TruthLabel systemLabel,
      TruthLabel primaryLabel,
      TruthLabel aiLabel,
      String consistency,
      String dissentingSource,
      int riskRank,
      List<String> reasonCodes) {}

  public record Claim(@NotBlank String reviewerId, long expectedVersion) {}

  public record Finalize(
      @NotBlank String reviewerId,
      long expectedVersion,
      List<TruthTarget> targets,
      boolean highRisk,
      String electronicSignature) {}

  public record TruthTarget(
      String targetCode,
      TruthLabel label,
      String reasonCode,
      String remark,
      TruthLabel systemLabel,
      TruthLabel primaryLabel,
      TruthLabel aiLabel) {}
}
