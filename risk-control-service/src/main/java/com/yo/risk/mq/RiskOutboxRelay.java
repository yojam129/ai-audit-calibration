package com.yo.risk.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.risk.domain.po.RiskOutbox;
import com.yo.risk.mapper.RiskOutboxMapper;
import java.time.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RiskOutboxRelay {
  private final RiskOutboxMapper mapper;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public RiskOutboxRelay(RiskOutboxMapper m, RabbitTemplate r, ObjectMapper j) {
    mapper = m;
    rabbit = r;
    json = j;
  }

  @Scheduled(fixedDelayString = "${risk.outbox-delay:1000}")
  public void relay() {
    mapper
        .selectList(
            new QueryWrapper<RiskOutbox>()
                .in("status", "PENDING", "RETRY")
                .le("next_attempt_at", Instant.now())
                .last("limit 100"))
        .forEach(this::send);
  }

  private void send(RiskOutbox e) {
    try {
      var c = new CorrelationData(e.id.toString());
      rabbit.convertAndSend(
          "ai.audit.domain", e.routingKey, json.readValue(e.payload, Map.class), c);
      if (!c.getFuture().get(10, TimeUnit.SECONDS).isAck()) throw new IllegalStateException("nack");
      e.status = "PUBLISHED";
      e.publishedAt = Instant.now();
    } catch (Exception x) {
      e.status = "RETRY";
      e.attempts++;
      e.nextAttemptAt = Instant.now().plusSeconds(Math.min(300, 1L << Math.min(8, e.attempts)));
    }
    mapper.updateById(e);
  }
}
