package com.yo.reviewworkflow.enums;

public final class ReviewEnums {
  private ReviewEnums() {}

  public enum Status {
    AI_PENDING,
    PRIMARY_PENDING,
    COMPARISON_PENDING,
    AUTO_ARCHIVING,
    PENDING,
    ESCALATED,
    IN_REVIEW,
    RETEST_REQUESTED,
    FINALIZED,
    ARCHIVED,
    SLA_BREACHED
  }

  public enum TruthLabel {
    POSITIVE,
    NEGATIVE,
    INDETERMINATE,
    INVALID
  }
}
