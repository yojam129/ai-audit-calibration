package com.yo.sample.mq;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class AuditWorkflowEvent {
  private UUID eventId;
  private UUID sampleId;
  private String runNo;
  private Long primaryTaskId;
  private String stage;
  private Instant occurredAt;
}
