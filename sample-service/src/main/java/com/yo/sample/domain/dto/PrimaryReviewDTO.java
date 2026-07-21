package com.yo.sample.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class PrimaryReviewDTO {
  private PrimaryReviewDTO() {}

  public record Claim(long expectedVersion) {}

  public record Submit(long expectedVersion, @NotEmpty List<TargetVerdict> targets) {}

  public record TargetVerdict(
      @NotBlank String chamber,
      @NotBlank String targetCode,
      @NotBlank String label,
      @NotBlank String reasonCode,
      String remark) {}
}
