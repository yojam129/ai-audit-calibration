package com.yo.risk.mq;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class GroundTruthConfirmedEvent {
  private UUID eventId;
  private UUID sampleId;
  private List<Outcome> outcomes;
  private Instant occurredAt;

  @Data
  public static class Outcome {
    private String sourceType;
    private String targetCode;
    private String predictedLabel;
    private String truthLabel;
    private String reviewerId;
    private Long authUserId;
    private long durationMs;
  }
}
