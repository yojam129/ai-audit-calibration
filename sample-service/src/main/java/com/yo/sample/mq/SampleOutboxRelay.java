package com.yo.sample.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.sample.domain.po.SampleOutboxEvent;
import com.yo.sample.mapper.SampleOutboxMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SampleOutboxRelay {
  private final SampleOutboxMapper mapper;
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;
  private final Duration maximumRetry;
  private final TransactionTemplate transactions;

  public SampleOutboxRelay(
      SampleOutboxMapper mapper,
      RabbitTemplate rabbit,
      ObjectMapper json,
      PlatformTransactionManager transactionManager,
      @Value("${app.outbox.maximum-retry:PT5M}") Duration maximumRetry) {
    this.mapper = mapper;
    this.rabbit = rabbit;
    this.json = json;
    this.maximumRetry = maximumRetry;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Scheduled(fixedDelayString = "${app.outbox.relay-delay:1000}")
  public void relay() {
    mapper.recoverStaleClaims();
    mapper
        .selectList(
            new QueryWrapper<SampleOutboxEvent>()
                .in("status", "PENDING", "RETRY")
                .le("next_attempt_at", Instant.now())
                .orderByAsc("created_at")
                .last("LIMIT 100"))
        .forEach(
            event ->
                transactions.executeWithoutResult(
                    ignored -> publish(event)));
  }

  void publish(SampleOutboxEvent candidate) {
    if (mapper.claim(candidate.id) != 1) return;
    SampleOutboxEvent event = mapper.selectById(candidate.id);
    try {
      CorrelationData correlation = new CorrelationData(event.id.toString());
      Object payload = event.routingKey.startsWith("sample.audit.")
          ? json.readValue(event.payload, AuditWorkflowEvent.class)
          : json.readValue(event.payload, DetectionTargetCompletedEvent.class);
      rabbit.convertAndSend(
          "ai.audit.domain",
          event.routingKey,
          payload,
          correlation);
      CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
      if (!confirm.isAck()) throw new IllegalStateException(confirm.getReason());
      event.status = "PUBLISHED";
      event.publishedAt = Instant.now();
      event.lastError = null;
    } catch (Exception failure) {
      event.status = "RETRY";
      event.attempts++;
      event.nextAttemptAt =
          Instant.now().plus(OutboxRetryPolicy.delay(event.attempts, maximumRetry));
      String message =
          failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
      event.lastError = message.substring(0, Math.min(1000, message.length()));
    }
    mapper.updateById(event);
  }
}
