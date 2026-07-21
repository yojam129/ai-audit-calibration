package com.yo.risk.mq;

import com.yo.risk.domain.dto.ReviewOutcomeDTO;
import com.yo.risk.service.RiskService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewerOutcomeListener {
  private final RiskService service;

  public ReviewerOutcomeListener(RiskService service) {
    this.service = service;
  }

  @RabbitListener(queues = "${app.queue.reviewer-outcome:risk.reviewer-outcome.v1}")
  public void consume(ReviewOutcomeDTO event) {
    service.record(event);
  }
}
