package com.yo.sample.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.signal.SignalClient;
import com.yo.sample.domain.dto.PrimaryReviewDTO;
import com.yo.sample.domain.po.*;
import com.yo.sample.domain.vo.PrimaryReviewTaskVO;
import com.yo.sample.mapper.*;
import com.yo.sample.mq.AuditWorkflowEvent;
import com.yo.sample.service.PrimaryReviewService;
import com.yo.security.context.CurrentUserContext;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrimaryReviewServiceImpl implements PrimaryReviewService {
  private final PrimaryReviewTaskMapper tasks;
  private final SampleMapper samples;
  private final InstrumentRunMapper runs;
  private final TargetJudgementMapper systemTargets;
  private final SignalClient signals;
  private final ObjectMapper json;
  private final SampleOutboxMapper outbox;

  public PrimaryReviewServiceImpl(
      PrimaryReviewTaskMapper tasks, SampleMapper samples, InstrumentRunMapper runs,
      TargetJudgementMapper systemTargets, SignalClient signals, ObjectMapper json,
      SampleOutboxMapper outbox) {
    this.tasks = tasks;
    this.samples = samples;
    this.runs = runs;
    this.systemTargets = systemTargets;
    this.signals = signals;
    this.json = json;
    this.outbox = outbox;
  }

  @Override
  public IPage<PrimaryReviewTaskVO> page(long current, long size, String status) {
    var user = CurrentUserContext.required();
    var query = Wrappers.<PrimaryReviewTask>lambdaQuery()
        .eq(status != null && !status.isBlank(), PrimaryReviewTask::getStatus, status)
        .and(!user.hasRole("QUALITY_MANAGER") && !user.hasRole("SUPER_ADMIN"),
            wrapper -> wrapper.eq(PrimaryReviewTask::getStatus, "PENDING")
                .or().eq(PrimaryReviewTask::getReviewerAuthUserId, user.userId()))
        .orderByDesc(PrimaryReviewTask::getCreatedAt);
    return tasks.selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), query)
        .convert(this::vo);
  }

  @Override public PrimaryReviewTaskVO detail(long id) {
    PrimaryReviewTask task = required(id);
    var user = CurrentUserContext.required();
    if (!user.hasRole("QUALITY_MANAGER") && !user.hasRole("SUPER_ADMIN")
        && task.reviewerAuthUserId != null && !task.reviewerAuthUserId.equals(user.userId()))
      throw new SecurityException("primary review task belongs to another reviewer");
    return vo(task);
  }

  @Override
  @Transactional
  public PrimaryReviewTaskVO claim(long id, PrimaryReviewDTO.Claim request) {
    var user = CurrentUserContext.required();
    PrimaryReviewTask task = required(id);
    if (!"PENDING".equals(task.status) || task.version != request.expectedVersion())
      throw new IllegalStateException("primary review task state/version conflict");
    if (tasks.claim(id, request.expectedVersion(), user.userId(), user.username(), LocalDateTime.now()) != 1)
      throw new IllegalStateException("primary review task state/version conflict");
    samples.updateById(updateSampleStatus(task.sampleId, "PRIMARY_IN_REVIEW"));
    return vo(required(id));
  }

  @Override
  @Transactional
  public PrimaryReviewTaskVO submit(long id, PrimaryReviewDTO.Submit request) {
    var user = CurrentUserContext.required();
    PrimaryReviewTask task = required(id);
    if (!"IN_REVIEW".equals(task.status) || task.version != request.expectedVersion()
        || !Objects.equals(task.reviewerAuthUserId, user.userId()))
      throw new IllegalStateException("only the authenticated task owner may submit");
    List<TargetJudgement> expected = systemTargets.selectList(
        Wrappers.<TargetJudgement>query().eq("run_id", task.runId));
    Set<String> expectedCodes = new HashSet<>();
    expected.forEach(item -> expectedCodes.add(item.chamber + "|" + item.targetCode));
    Set<String> submittedCodes = new HashSet<>();
    request.targets().forEach(item -> {
      String key = item.chamber() + "|" + item.targetCode();
      if (!submittedCodes.add(key))
        throw new IllegalArgumentException("duplicate target: " + key);
      normalizeLabel(item.label());
    });
    if (!expectedCodes.equals(submittedCodes))
      throw new IllegalArgumentException("primary verdict must cover every target");
    String targetsJson;
    try {
      targetsJson = json.writeValueAsString(request.targets());
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid primary verdict", ex);
    }
    LocalDateTime submittedAt = LocalDateTime.now();
    long durationMs = Math.max(0, Duration.between(task.claimedAt, submittedAt).toMillis());
    if (tasks.submit(id, request.expectedVersion(), user.userId(), targetsJson, submittedAt, durationMs) != 1)
      throw new IllegalStateException("primary review task state/version conflict");
    PrimaryReviewTask submitted = required(id);
    Sample sample = updateSampleStatus(task.sampleId, "COMPARISON_PENDING");
    samples.updateById(sample);
    enqueuePrimaryCompleted(sample, runs.selectById(task.runId), submitted);
    return vo(submitted);
  }

  private Sample updateSampleStatus(long sampleId, String status) {
    Sample sample = Optional.ofNullable(samples.selectById(sampleId)).orElseThrow();
    if ("ARCHIVED".equals(sample.status))
      throw new IllegalStateException("archived sample cannot enter another workflow step");
    sample.status = status;
    return sample;
  }

  private void enqueuePrimaryCompleted(
      Sample sample, InstrumentRun run, PrimaryReviewTask task) {
    AuditWorkflowEvent event = new AuditWorkflowEvent();
    event.setEventId(UUID.randomUUID());
    event.setSampleId(UUID.fromString(sample.businessId));
    event.setRunNo(run.runNo);
    event.setPrimaryTaskId(task.id);
    event.setStage("PRIMARY_COMPLETED");
    event.setOccurredAt(Instant.now());
    SampleOutboxEvent pending = new SampleOutboxEvent();
    pending.id = event.getEventId().toString();
    pending.aggregateType = "SAMPLE_AUDIT";
    pending.aggregateId = sample.businessId;
    pending.eventType = event.getStage();
    pending.routingKey = "sample.audit.primary-completed.v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception ex) {
      throw new IllegalStateException("workflow event serialization failed", ex);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
  }

  private PrimaryReviewTask required(long id) {
    return Optional.ofNullable(tasks.selectById(id)).orElseThrow();
  }

  private PrimaryReviewTaskVO vo(PrimaryReviewTask task) {
    boolean primaryReviewer = CurrentUserContext.required().hasRole("PRIMARY_REVIEWER");
    Sample sample = samples.selectById(task.sampleId);
    InstrumentRun run = runs.selectById(task.runId);
    List<SignalClient.AiResult> ai = List.of();
    if (!primaryReviewer) {
      try {
        var response = signals.aiResults(run.runNo);
        ai = response == null || response.data() == null ? List.of() : response.data();
      } catch (RuntimeException unavailable) {
        ai = List.of();
      }
    }
    Map<String, SignalClient.AiResult> aiByTarget = new HashMap<>();
    ai.forEach(item -> aiByTarget.put(item.chamber() + "|" + item.targetCode(), item));
    var evidence = systemTargets.selectList(Wrappers.<TargetJudgement>query().eq("run_id", task.runId))
        .stream().map(item -> {
          var result = aiByTarget.get(item.chamber + "|" + item.targetCode);
          return new PrimaryReviewTaskVO.TargetEvidence(
              item.chamber, item.channelCode, item.targetCode,
              primaryReviewer ? null : normalizeLabel(item.systemJudgement), item.ctValue,
              item.concentrationValue, item.concentrationUnit, item.riskLevel,
              item.riskFlags == null || item.riskFlags.isBlank()
                  ? List.of() : List.of(item.riskFlags.split(",")),
              result == null ? null : result.judgement(),
              result == null ? null : result.confidence(),
              result == null ? null : result.evidenceJson(),
              primaryReviewer ? null : result == null ? "UNAVAILABLE" : result.status());
        }).toList();
    return new PrimaryReviewTaskVO(task.id, sample.id, sample.businessId, sample.sampleNo,
        run.runNo, run.startedAt, run.endedAt, task.status, task.reviewerName, task.version,
        task.createdAt, evidence);
  }

  public static String normalizeLabel(String value) {
    if (value == null) return "INDETERMINATE";
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "POSITIVE", "阳性", "+" -> "POSITIVE";
      case "NEGATIVE", "阴性", "-" -> "NEGATIVE";
      case "INVALID", "无效" -> "INVALID";
      default -> "INDETERMINATE";
    };
  }
}
