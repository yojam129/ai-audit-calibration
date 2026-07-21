package com.yo.judgement.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.judgement.domain.po.OutboxEventPO;
import com.yo.judgement.mapper.OutboxEventMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxRelay {
  private final OutboxEventMapper mapper;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public OutboxRelay(OutboxEventMapper mapper, RabbitTemplate rabbit, ObjectMapper json) {
    this.mapper = mapper;
    this.rabbit = rabbit;
    this.json = json;
  }

  @Scheduled(fixedDelayString = "${app.outbox.relay-delay:1000}")
  public void relay() {
    var due =
        mapper.selectList(
            new QueryWrapper<OutboxEventPO>()
                .in("status", "PENDING", "RETRY")
                .le("next_attempt_at", Instant.now())
                .orderByAsc("created_at")
                .last("limit 100"));
    due.forEach(this::publish);
  }

  @Transactional
  public void publish(OutboxEventPO event) {
    if (mapper.claim(event.id) != 1) return;
    var current = mapper.selectById(event.id);
    try {
      var correlation = new CorrelationData(event.id.toString());
      rabbit.convertAndSend(
          "ai.audit.domain",
          event.routingKey,
          json.readValue(event.payload, ComparisonPublisher.Event.class),
          correlation);
      var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
      if (!confirm.isAck()) throw new IllegalStateException(confirm.getReason());
      current.status = "PUBLISHED";
      current.publishedAt = Instant.now();
      current.lastError = null;
    } catch (Exception ex) {
      current.status = "RETRY";
      current.attempts++;
      current.nextAttemptAt =
          Instant.now()
              .plus(Math.min(300, 1L << Math.min(current.attempts, 8)), ChronoUnit.SECONDS);
      current.lastError = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
    mapper.updateById(current);
  }
}
