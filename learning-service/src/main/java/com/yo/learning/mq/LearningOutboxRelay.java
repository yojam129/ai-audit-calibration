package com.yo.learning.mq;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.auth.AuthPermissionClient;
import com.yo.learning.domain.po.LearningOutbox;
import com.yo.learning.mapper.LearningOutboxMapper;
import com.yo.learning.mapper.LearningMapper;
import com.yo.learning.infrastructure.LearningFlowableClient;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class LearningOutboxRelay {
  private final LearningOutboxMapper mapper;
  private final AuthPermissionClient auth;
  private final LearningMapper assignments;
  private final ObjectMapper json;
  private final Duration maximumRetry;
  private final TransactionTemplate transactions;
  private final LearningFlowableClient flowable;

  public LearningOutboxRelay(
      LearningOutboxMapper mapper,
      LearningMapper assignments,
      AuthPermissionClient auth,
      ObjectMapper json,
      PlatformTransactionManager transactionManager,
      LearningFlowableClient flowable,
      @Value("${app.outbox.maximum-retry:PT5M}") Duration maximumRetry) {
    this.mapper = mapper;
    this.assignments = assignments;
    this.auth = auth;
    this.json = json;
    this.maximumRetry = maximumRetry;
    this.transactions = new TransactionTemplate(transactionManager);
    this.flowable = flowable;
  }

  @Scheduled(fixedDelayString = "${app.outbox.relay-delay:1000}")
  public void relay() {
    mapper.recoverStale();
    mapper
        .selectList(
            new QueryWrapper<LearningOutbox>()
                .in(
                    "event_type",
                    "AUTH_PERMISSION_FREEZE",
                    "AUTH_PERMISSION_RESTORE",
                    "FLOWABLE_PROCESS_START")
                .in("status", "PENDING", "RETRY")
                .le("next_attempt_at", Instant.now())
                .orderByAsc("created_at")
                .last("LIMIT 100"))
        .forEach(event -> transactions.executeWithoutResult(ignored -> deliver(event)));
  }

  void deliver(LearningOutbox candidate) {
    if (mapper.claim(candidate.id) != 1) return;
    LearningOutbox event = mapper.selectById(candidate.id);
    try {
      if ("FLOWABLE_PROCESS_START".equals(event.eventType)) {
        startWorkflow(event);
        markPublished(event);
        mapper.updateById(event);
        return;
      }
      PermissionOperation payload =
          json.readValue(event.payloadJson, PermissionOperation.class);
      var command =
          new AuthPermissionClient.PermissionChange(
              event.eventId,
              payload.authUserId(),
              payload.permissionCode(),
              payload.reason(),
              payload.approvedByAuthUserId());
      if ("AUTH_PERMISSION_FREEZE".equals(event.eventType)) {
        auth.freeze(command);
      } else {
        auth.restore(command);
        var assignment = assignments.selectById(Long.valueOf(event.aggregateId));
        if (assignment == null)
          throw new IllegalStateException("restore assignment no longer exists");
        assignment.status = "RESTORED";
        assignments.updateById(assignment);
      }
      markPublished(event);
    } catch (Exception failure) {
      event.status = "RETRY";
      event.attempts++;
      event.nextAttemptAt = Instant.now().plus(retryDelay(event.attempts, maximumRetry));
      String message =
          failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
      event.lastError = message.substring(0, Math.min(1000, message.length()));
    }
    mapper.updateById(event);
  }

  private void startWorkflow(LearningOutbox event) {
    var assignment = assignments.selectById(Long.valueOf(event.aggregateId));
    if (assignment == null) throw new IllegalStateException("learning assignment no longer exists");
    if (assignment.processInstanceId != null && !assignment.processInstanceId.isBlank()) return;
    assignment.processInstanceId = flowable.start(assignment);
    assignment.workflowStartedAt = Instant.now();
    assignments.updateById(assignment);
  }

  private void markPublished(LearningOutbox event) {
    event.status = "PUBLISHED";
    event.publishedAt = Instant.now();
    event.lastError = null;
  }

  static Duration retryDelay(int attempts, Duration maximum) {
    return Duration.ofSeconds(
        Math.min(maximum.toSeconds(), 1L << Math.min(Math.max(1, attempts), 20)));
  }

  record PermissionOperation(
      Long authUserId,
      String permissionCode,
      String reason,
      Long approvedByAuthUserId) {}
}
