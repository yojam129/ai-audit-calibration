package com.yo.statistics.mq;

import java.time.*;
import java.util.*;

public record TruthMetricEvent(
    UUID eventId,
    UUID sampleId,
    long truthVersion,
    boolean archived,
    boolean secondaryTruthConfirmed,
    Instant archivedAt,
    List<Outcome> outcomes,
    Instant occurredAt) {
  public record Outcome(
      String sourceType,
      String targetCode,
      String predictedLabel,
      String truthLabel,
      String reviewerId,
      Long authUserId,
      long durationMs) {}
}
