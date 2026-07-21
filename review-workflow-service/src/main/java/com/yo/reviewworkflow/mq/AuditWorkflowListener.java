package com.yo.reviewworkflow.mq;

import com.yo.reviewworkflow.service.ReviewService;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AuditWorkflowListener {
  private final ReviewService reviews;

  public AuditWorkflowListener(ReviewService reviews) {
    this.reviews = reviews;
  }

  @RabbitListener(
      queues = {
        "${app.queue.audit-imported:review.audit.imported.v1}",
        "${app.queue.audit-ai-completed:review.audit.ai-completed.v1}",
        "${app.queue.audit-primary-completed:review.audit.primary-completed.v1}"
      })
  public void receive(Event event) {
    reviews.advanceWorkflow(
        event.getSampleId(), event.getRunNo(), event.getPrimaryTaskId(), event.getStage());
  }

  @Data
  public static class Event {
    private UUID eventId;
    private UUID sampleId;
    private String runNo;
    private Long primaryTaskId;
    private String stage;
    private Instant occurredAt;
  }
}
