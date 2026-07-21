package com.yo.signal.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.signal.domain.po.InferenceOutbox;
import com.yo.signal.mapper.InferenceOutboxMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InferenceOutboxRelay {
  private final InferenceOutboxMapper mapper;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;

  public InferenceOutboxRelay(InferenceOutboxMapper m, RabbitTemplate r, ObjectMapper j) {
    mapper = m;
    rabbit = r;
    json = j;
  }

  @Scheduled(fixedDelayString = "${app.ai-inference.outbox-delay:1000}")
  public void relay() {
    mapper
        .selectList(
            new QueryWrapper<InferenceOutbox>()
                .in("status", "NEW", "RETRY")
                .le("next_attempt_at", LocalDateTime.now())
                .last("limit 100"))
        .forEach(this::publish);
  }

  private void publish(InferenceOutbox e) {
    try {
      var c = new CorrelationData(e.eventId);
      rabbit.convertAndSend(
          "ai.audit.domain",
          "ai.inference.completed.v1",
          json.readValue(e.payloadJson, Map.class),
          c);
      var confirm = c.getFuture().get(10, TimeUnit.SECONDS);
      if (!confirm.isAck()) throw new IllegalStateException(confirm.getReason());
      e.status = "PUBLISHED";
      e.publishedAt = LocalDateTime.now();
      e.lastError = null;
    } catch (Exception x) {
      e.status = "RETRY";
      e.attempts++;
      e.nextAttemptAt =
          LocalDateTime.now().plusSeconds(Math.min(300, 1L << Math.min(e.attempts, 8)));
      e.lastError =
          (x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage())
              .substring(
                  0,
                  Math.min(
                      500,
                      (x.getMessage() == null ? x.getClass().getSimpleName() : x.getMessage())
                          .length()));
    }
    mapper.updateById(e);
  }
}
