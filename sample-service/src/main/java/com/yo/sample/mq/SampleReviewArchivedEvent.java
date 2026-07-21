package com.yo.sample.mq;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class SampleReviewArchivedEvent {
  private UUID eventId;
  private UUID sampleId;
  private UUID reviewTaskId;
  private String consistency;
  private boolean archived;
  private boolean secondaryTruthConfirmed;
  private Instant archivedAt;
}
