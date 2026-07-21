package com.yo.reviewworkflow.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.reviewworkflow.domain.po.ReviewOutboxPO;
import com.yo.reviewworkflow.mapper.ReviewMappers.Outbox;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewOutboxRelay {
  private final Outbox outbox;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public ReviewOutboxRelay(Outbox outbox, RabbitTemplate rabbit, ObjectMapper json) {
    this.outbox = outbox;
    this.rabbit = rabbit;
    this.json = json;
  }

  @Scheduled(fixedDelayString = "${app.outbox.relay-delay:1000}")
  public void relay() {
    outbox
        .selectList(
            new QueryWrapper<ReviewOutboxPO>()
                .in("status", "PENDING", "RETRY")
                .le("next_attempt_at", Instant.now())
                .last("limit 100"))
        .forEach(this::publish);
  }

  private void publish(ReviewOutboxPO e) {
    try {
      var correlation = new CorrelationData(e.id.toString());
      rabbit.convertAndSend(
          "ai.audit.domain", e.routingKey, json.readValue(e.payload, Map.class), correlation);
      var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
      if (!confirm.isAck()) throw new IllegalStateException(confirm.getReason());
      e.status = "PUBLISHED";
      e.publishedAt = Instant.now();
    } catch (Exception ex) {
      e.status = "RETRY";
      e.attempts++;
      e.nextAttemptAt =
          Instant.now().plus(Math.min(300, 1L << Math.min(e.attempts, 8)), ChronoUnit.SECONDS);
      e.lastError = ex.getMessage();
    }
    outbox.updateById(e);
  }
}
