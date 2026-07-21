package com.yo.judgement.mq;

import com.yo.judgement.domain.vo.ComparisonVO;
import java.time.*;
import java.util.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ComparisonPublisher {
  private final RabbitTemplate rabbit;

  public ComparisonPublisher(RabbitTemplate r) {
    rabbit = r;
  }

  public void publish(ComparisonVO v) {
    rabbit.convertAndSend(
        "ai.audit.domain",
        "comparison.completed.v1",
        new Event(
            UUID.randomUUID(),
            v.sampleId(),
            v.comparisonVersion(),
            v.primaryReviewerId(),
            v.primaryAuthUserId(),
            v.primaryDurationMs(),
            v.consistency().name(),
            v.riskRank(),
            v.reasonCodes(),
            v.targets(),
            Instant.now()));
  }

  public record Event(
      UUID eventId,
      UUID sampleId,
      long comparisonVersion,
      String primaryReviewerId,
      Long primaryAuthUserId,
      long primaryDurationMs,
      String consistency,
      int riskRank,
      List<String> reasonCodes,
      List<ComparisonVO.TargetVO> targets,
      Instant occurredAt) {}
}
