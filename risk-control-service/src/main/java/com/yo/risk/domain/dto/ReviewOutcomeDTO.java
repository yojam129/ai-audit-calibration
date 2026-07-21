package com.yo.risk.domain.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record ReviewOutcomeDTO(
    @NotBlank String eventId,
    @NotBlank String reviewerId,
    @NotNull Long authUserId,
    boolean correct,
    @PositiveOrZero long durationMs,
    String errorType,
    @NotNull Instant occurredAt,
    String sampleId,
    String sampleNo,
    String chamber,
    String channelCode,
    String targetCode,
    String predictedLabel,
    String truthLabel) {
  public ReviewOutcomeDTO(
      String eventId,
      String reviewerId,
      Long authUserId,
      boolean correct,
      long durationMs,
      String errorType,
      Instant occurredAt) {
    this(eventId, reviewerId, authUserId, correct, durationMs, errorType, occurredAt,
        null, null, null, null, null, null, null);
  }
}
