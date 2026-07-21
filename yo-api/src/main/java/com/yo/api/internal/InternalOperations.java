package com.yo.api.internal;

public final class InternalOperations {
  private InternalOperations() {}

  public record OperationResult(String operation, long affected, String detail) {}

  public static final String RECONCILE_IMPORTS = "/internal/v1/imports/reconcile";
  public static final String SCAN_REVIEW_SLA = "/internal/v1/reviews/sla/scan";
  public static final String RECOVER_OUTBOX = "/internal/v1/outbox/recover";
  public static final String TRAIN_AI_FEEDBACK = "/internal/v1/ai-training/incremental";
}
