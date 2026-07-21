package com.yo.reviewworkflow.mq;

import com.yo.reviewworkflow.domain.dto.ReviewDTO;
import com.yo.reviewworkflow.service.*;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ComparisonReviewListener {
  private final ReviewService reviews;
  private final ReviewRoutingPolicy routing;

  public ComparisonReviewListener(ReviewService reviews, ReviewRoutingPolicy routing) {
    this.reviews = reviews;
    this.routing = routing;
  }

  @RabbitListener(queues = "${app.queue.review-comparison:review.comparison.v1}")
  public void receive(ComparisonEvent event) {
    var route = routing.route(event.sampleId(), event.consistency(), event.riskRank());
    reviews.create(
        new ReviewDTO.Create(
            event.sampleId(), event.primaryReviewerId(), event.primaryAuthUserId(),
            event.primaryDurationMs(), route.priority(), event.consistency(), route.sampling(), event.targets()));
  }

  public record ComparisonEvent(
      UUID eventId,
      UUID sampleId,
      long comparisonVersion,
      String primaryReviewerId,
      Long primaryAuthUserId,
      long primaryDurationMs,
      String consistency,
      int riskRank,
      java.util.List<String> reasonCodes,
      java.util.List<ReviewDTO.SourceTarget> targets,
      java.time.Instant occurredAt) {}
}
