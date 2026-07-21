package com.yo.sample.mq;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DetectionTargetCompletedEvent(
    UUID eventId,
    String organizationId,
    String orderId,
    String instrumentNo,
    String panelCode,
    String reagentLotNo,
    Instant occurredAt,
    List<TargetResult> targets) {
  public record TargetResult(
      String targetCode,
      String resultLabel,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel) {}
}
