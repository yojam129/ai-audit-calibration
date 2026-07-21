package com.yo.reviewworkflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.*;
import com.yo.api.client.sample.SampleClient;
import com.yo.reviewworkflow.domain.dto.*;
import com.yo.reviewworkflow.domain.po.*;
import com.yo.reviewworkflow.domain.vo.*;
import com.yo.reviewworkflow.enums.ReviewEnums.*;
import com.yo.reviewworkflow.mapper.ReviewMappers.*;
import com.yo.reviewworkflow.service.*;
import java.time.*;
import java.util.*;
import com.yo.reviewworkflow.infrastructure.FlowableRestClient;
import org.springframework.amqp.rabbit.core.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import com.yo.security.context.CurrentUserContext;

@Service
public class ReviewServiceImpl implements ReviewService {
  private final Task tasks;
  private final Truth truths;
  private final FlowableRestClient flowable;
  
  private final RabbitTemplate rabbit;
  private final ObjectMapper json;
  private final Outbox outbox;
  private final SampleClient samples;

  public ReviewServiceImpl(
      Task t,
      Truth g,
      Outbox outbox,
      FlowableRestClient flowable,
      SampleClient samples,
      RabbitTemplate q,
      ObjectMapper j) {
    tasks = t;
    truths = g;
    this.outbox = outbox;
    this.flowable = flowable;
    this.samples = samples;
    rabbit = q;
    json = j;
  }

  @Override
  @Transactional
  public void advanceWorkflow(
      UUID sampleId, String runNo, Long primaryTaskId, String stage) {
    ReviewTaskPO task = ensureWorkflow(sampleId, runNo, primaryTaskId);
    if ("IMPORT_COMPLETED".equals(stage)) return;
    if ("AI_COMPLETED".equals(stage)) {
      flowable.signal(task.processInstanceId, "awaitAi", Map.of("aiCompleted", true));
      if (!Status.ARCHIVED.name().equals(task.status)) {
        task.status = Status.PRIMARY_PENDING.name();
        tasks.updateById(task);
      }
      return;
    }
    if ("PRIMARY_COMPLETED".equals(stage)) {
      flowable.signal(task.processInstanceId, "awaitAi", Map.of("aiCompleted", true));
      var primary = flowable.queryTask(task.processInstanceId, "primaryReview");
      if (primary != null) {
        flowable.completeTask(
            primary.id(), Map.of("primaryCompleted", true, "primaryTaskId", primaryTaskId));
      }
      if (!Status.ARCHIVED.name().equals(task.status)) {
        task.status = Status.COMPARISON_PENDING.name();
        tasks.updateById(task);
      }
    }
  }

  private ReviewTaskPO ensureWorkflow(UUID sampleId, String runNo, Long primaryTaskId) {
    ReviewTaskPO existing = tasks.selectOne(
        Wrappers.<ReviewTaskPO>lambdaQuery()
            .eq(ReviewTaskPO::getSampleId, sampleId)
            .last("LIMIT 1"));
    if (existing != null) return existing;
    ReviewTaskPO task = new ReviewTaskPO();
    task.id = UUID.randomUUID();
    task.sampleId = sampleId;
    task.runNo = runNo;
    task.primaryTaskId = primaryTaskId;
    task.status = Status.AI_PENDING.name();
    task.priority = "P2";
    task.consistency = "PENDING";
    task.sourceTargetsJson = "[]";
    task.createdAt = Instant.now();
    task.slaDueAt = task.createdAt.plus(Duration.ofHours(2));
    Map<String, Object> variables = new HashMap<>();
    variables.put("reviewTaskId", task.id.toString());
    variables.put("sampleId", sampleId.toString());
    variables.put("runNo", Objects.toString(runNo, ""));
    variables.put("primaryTaskId", primaryTaskId == null ? 0L : primaryTaskId);
    variables.put("priority", "P2");
    variables.put("consistency", "PENDING");
    variables.put("sampling", false);
    variables.put("finalTruthComplete", false);
    task.processInstanceId = flowable.startProcess("sampleAuditMain", variables);
    tasks.insert(task);
    return task;
  }

  @Transactional
  public ReviewTaskVO create(ReviewDTO.Create request) {
    UUID sample = request.sampleId();
    var p = ensureWorkflow(sample, null, null);
    if (Status.ARCHIVED.name().equals(p.status)
        || Status.PENDING.name().equals(p.status)
        || Status.ESCALATED.name().equals(p.status)
        || Status.IN_REVIEW.name().equals(p.status)) return vo(p);
    p.primaryReviewerId = request.primaryReviewerId();
    p.primaryAuthUserId = request.primaryAuthUserId();
    p.primaryDurationMs = request.primaryDurationMs();
    p.consistency = request.consistency() == null ? "TWO_AGREE_ONE_DIFF" : request.consistency();
    boolean forcedP1 = "ALL_DIFFERENT".equals(p.consistency)
        || request.sourceTargets() != null
            && request.sourceTargets().stream().anyMatch(target -> target.riskRank() >= 4);
    p.priority = forcedP1 ? "P1" : request.priority() == null ? "P2" : request.priority();
    try {
      p.sourceTargetsJson = json.writeValueAsString(
          request.sourceTargets() == null ? List.of() : request.sourceTargets());
    } catch (Exception ex) {
      throw new IllegalArgumentException("invalid comparison target snapshot", ex);
    }
    p.slaDueAt =
        Instant.now().plus("P1".equals(p.priority) ? Duration.ofMinutes(15) : Duration.ofHours(2));
    p.status = Status.COMPARISON_PENDING.name();
    tasks.updateById(p);
    flowable.signal(p.processInstanceId, "awaitAi", Map.of("aiCompleted", true));
    var primary = flowable.queryTask(p.processInstanceId, "primaryReview");
    if (primary != null) {
      flowable.completeTask(primary.id(), Map.of("primaryCompleted", true));
    }
    Map<String, Object> variables = new HashMap<>();
    variables.put("comparisonCompleted", true);
    variables.put("consistency", p.consistency);
    variables.put("priority", p.priority);
    variables.put("sampling", request.sampling());
    flowable.signal(p.processInstanceId, "awaitComparison", variables);
    if (flowable.queryExecution(p.processInstanceId, "archiveCallback") != null) {
      archiveAutomatic(p);
    } else {
      var secondary = flowable.queryTasks(p.processInstanceId).stream()
          .filter(task -> "secondaryReview".equals(task.taskDefinitionKey()))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Flowable did not create secondary review"));
      p.flowableTaskId = secondary.id();
      p.status = "P1".equals(p.priority) ? Status.ESCALATED.name() : Status.PENDING.name();
      tasks.updateById(p);
    }
    return vo(p);
  }

  private void archiveAutomatic(ReviewTaskPO task) {
    task.status = Status.AUTO_ARCHIVING.name();
    task.archivedAt = Instant.now();
    tasks.updateById(task);
    GroundTruthPO existingTruth = truths.selectOne(
        Wrappers.<GroundTruthPO>lambdaQuery()
            .eq(GroundTruthPO::getSampleId, task.sampleId)
            .last("LIMIT 1"));
    AutoConsensusTruth autoTruth = existingTruth == null
        ? persistAutoConsensusTruth(task, readSourceTargets(task.sourceTargetsJson))
        : new AutoConsensusTruth(existingTruth, readTruthTargets(existingTruth.targetsJson));
    task.status = Status.ARCHIVED.name();
    tasks.updateById(task);
    enqueueTruthEvent(task, autoTruth.truth(), autoTruth.targets(), false);
    enqueueArchiveEvent(task, false, task.archivedAt);
    if (!flowable.signal(task.processInstanceId, "archiveCallback", Map.of("archived", true)))
      throw new IllegalStateException("Flowable archive callback is not active");
  }

  @Transactional
  public ReviewTaskVO claim(UUID id, ReviewDTO.Claim d) {
    var current = CurrentUserContext.required();
    var p = tasks.selectById(id);
    require(p, d.expectedVersion(), Status.PENDING, Status.ESCALATED);
    if (p.flowableTaskId != null)
      flowable.claimTask(p.flowableTaskId, current.username());
    if (tasks.claim(id, d.expectedVersion(), d.reviewerId(), current.userId(), Instant.now()) != 1)
      throw new IllegalStateException("task state/version conflict");
    return vo(tasks.selectById(id));
  }

  @Transactional
  public GroundTruthPO finalizeTask(UUID id, ReviewDTO.Finalize d) {
    var current = CurrentUserContext.required();
    var p = tasks.selectById(id);
    require(p, d.expectedVersion(), Status.IN_REVIEW);
    if (p.ownerAuthUserId == null || !p.ownerAuthUserId.equals(current.userId()))
      throw new SecurityException("only the authenticated task owner may finalize");
    if (d.targets() == null
        || d.targets().isEmpty()
        || d.targets().stream()
            .anyMatch(x -> x.targetCode() == null || x.label() == null || x.reasonCode() == null))
      throw new IllegalArgumentException("target label/reason required");
    if ("P1".equals(p.priority) && !d.highRisk())
      throw new IllegalArgumentException("P1 review must remain high risk");
    if (("P1".equals(p.priority) || d.highRisk())
        && (d.electronicSignature() == null || d.electronicSignature().isBlank()))
      throw new IllegalArgumentException("signature required");
    long version =
        truths.selectCount(
                Wrappers.<GroundTruthPO>lambdaQuery()
                    .eq(GroundTruthPO::getSampleId, p.sampleId))
            + 1;
    var g = new GroundTruthPO();
    g.id = UUID.randomUUID();
    g.sampleId = p.sampleId;
    g.truthVersion = version;
    g.taskId = p.id;
    g.reviewerId = d.reviewerId();
    g.authUserId = current.userId();
    g.durationMs =
        Math.max(
            0,
            Duration.between(
                    p.claimedAt == null ? p.createdAt : p.claimedAt, Instant.now())
                .toMillis());
    List<ReviewDTO.SourceTarget> sourceTargets = readSourceTargets(p.sourceTargetsJson);
    Map<String, ReviewDTO.SourceTarget> sourceByCode = new HashMap<>();
    sourceTargets.forEach(target -> sourceByCode.put(target.targetCode(), target));
    Set<String> verdictCodes = new HashSet<>();
    d.targets().forEach(target -> {
      if (!verdictCodes.add(target.targetCode()))
        throw new IllegalArgumentException("duplicate final verdict target: " + target.targetCode());
    });
    if (!sourceTargets.isEmpty() && !verdictCodes.equals(sourceByCode.keySet()))
      throw new IllegalArgumentException("final verdict target set must match comparison snapshot");
    List<ReviewDTO.TruthTarget> verifiedTargets =
        d.targets().stream()
            .map(target -> mergeTrustedSources(target, sourceByCode.get(target.targetCode())))
            .toList();
    try {
      g.targetsJson = json.writeValueAsString(verifiedTargets);
    } catch (Exception e) {
      throw new IllegalArgumentException("invalid targets", e);
    }
    g.confirmedAt = Instant.now();
    var secondary = flowable.queryTasks(p.processInstanceId).stream()
        .filter(task -> "secondaryReview".equals(task.taskDefinitionKey()))
        .findFirst();
    if (secondary.isPresent()) {
      flowable.completeTask(
          secondary.get().id(),
          Map.of("finalTruthComplete", true, "secondaryTruthConfirmed", true));
    }
    if (flowable.queryExecution(p.processInstanceId, "archiveCallback") == null)
      throw new IllegalStateException("Flowable has not accepted the complete final truth");
    truths.insert(g);
    if (tasks.finalizeTask(id, d.expectedVersion(), current.userId(), g.confirmedAt) != 1)
      throw new IllegalStateException("task state/version conflict");
    enqueueTruthEvent(p, g, verifiedTargets, true);
    enqueueArchiveEvent(p, true, g.confirmedAt);
    if (!flowable.signal(p.processInstanceId, "archiveCallback", Map.of("archived", true)))
      throw new IllegalStateException("Flowable archive callback is not active");
    return g;
  }

  private AutoConsensusTruth persistAutoConsensusTruth(
      ReviewTaskPO task, List<ReviewDTO.SourceTarget> sources) {
    if (sources.isEmpty()
        || sources.stream()
            .anyMatch(
                target ->
                    target.systemLabel() == null
                        || target.systemLabel() != target.primaryLabel()
                        || target.systemLabel() != target.aiLabel())) {
      throw new IllegalStateException("auto archive requires complete three-way consensus");
    }
    List<ReviewDTO.TruthTarget> targets =
        sources.stream()
            .map(
                target ->
                    new ReviewDTO.TruthTarget(
                        target.targetCode(),
                        target.systemLabel(),
                        "AUTO_CONSENSUS",
                        "三方一致自动归档",
                        target.systemLabel(),
                        target.primaryLabel(),
                        target.aiLabel()))
            .toList();
    var truth = new GroundTruthPO();
    truth.id = UUID.randomUUID();
    truth.sampleId = task.sampleId;
    truth.truthVersion = 1;
    truth.taskId = task.id;
    truth.reviewerId = "AUTO_CONSENSUS";
    truth.authUserId = 0L;
    truth.durationMs = 0L;
    truth.confirmedAt = task.archivedAt;
    try {
      truth.targetsJson = json.writeValueAsString(targets);
    } catch (Exception ex) {
      throw new IllegalStateException("auto consensus truth serialization failed", ex);
    }
    truths.insert(truth);
    return new AutoConsensusTruth(truth, targets);
  }

  private void enqueueTruthEvent(
      ReviewTaskPO task,
      GroundTruthPO truth,
      List<ReviewDTO.TruthTarget> targets,
      boolean secondaryTruthConfirmed) {
    UUID eventId = UUID.randomUUID();
    var event =
        Map.of(
            "eventId", eventId,
            "sampleId", task.sampleId,
            "truthVersion", truth.truthVersion,
            "archived", true,
            "secondaryTruthConfirmed", secondaryTruthConfirmed,
            "archivedAt", truth.confirmedAt,
            "occurredAt", truth.confirmedAt,
            "targets", targets,
            "outcomes", targets.stream().flatMap(target -> outcomes(target, task)).toList());
    var pending = new ReviewOutboxPO();
    pending.id = eventId;
    pending.aggregateId = truth.id;
    pending.routingKey = "ground-truth.confirmed.v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception ex) {
      throw new IllegalStateException("truth event serialization failed", ex);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
  }

  private record AutoConsensusTruth(
      GroundTruthPO truth, List<ReviewDTO.TruthTarget> targets) {}

  private void enqueueArchiveEvent(
      ReviewTaskPO task, boolean secondaryTruthConfirmed, Instant archivedAt) {
    Instant effectiveArchivedAt = archivedAt == null ? task.archivedAt : archivedAt;
    UUID eventId = UUID.randomUUID();
    var event =
        Map.of(
            "eventId", eventId,
            "sampleId", task.sampleId,
            "reviewTaskId", task.id,
            "consistency", task.consistency,
            "archived", true,
            "secondaryTruthConfirmed", secondaryTruthConfirmed,
            "archivedAt", effectiveArchivedAt);
    var pending = new ReviewOutboxPO();
    pending.id = eventId;
    pending.aggregateId = task.id;
    pending.routingKey = "sample.review.archived.v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception ex) {
      throw new IllegalStateException("archive event serialization failed", ex);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
  }

  private java.util.stream.Stream<Map<String, Object>> outcomes(
      ReviewDTO.TruthTarget target, ReviewTaskPO task) {
    return java.util.stream.Stream.of(
            Map.entry("SYSTEM", target.systemLabel()),
            Map.entry("PRIMARY", target.primaryLabel()),
            Map.entry("AI", target.aiLabel()))
        .filter(x -> x.getValue() != null)
        .map(
            x ->
                Map.of(
                    "sourceType", x.getKey(),
                    "targetCode", target.targetCode(),
                    "predictedLabel", x.getValue().name(),
                    "truthLabel", target.label().name(),
                    "reviewerId", "PRIMARY".equals(x.getKey()) ? task.primaryReviewerId : "N/A",
                    "authUserId", "PRIMARY".equals(x.getKey()) ? task.primaryAuthUserId : 0L,
                    "durationMs", "PRIMARY".equals(x.getKey()) ? task.primaryDurationMs : 0L,
                    "correct", x.getValue() == target.label()));
  }

  @Transactional(readOnly = true)
  public IPage<ReviewTaskVO> page(long current, long size, String status) {
    var q = Wrappers.<ReviewTaskPO>lambdaQuery().orderByDesc(ReviewTaskPO::getId);
    if (status != null && !status.isBlank()) q.eq(ReviewTaskPO::getStatus, status);
    else q.in(
        ReviewTaskPO::getStatus,
        Status.PENDING.name(),
        Status.ESCALATED.name(),
        Status.IN_REVIEW.name(),
        Status.ARCHIVED.name());
    return tasks
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), q)
        .convert(this::vo);
  }

  @Transactional(readOnly = true)
  public ReviewTaskVO detail(UUID id) {
    var p = tasks.selectById(id);
    if (p == null) throw new NoSuchElementException("review task not found");
    return vo(p);
  }

  @Transactional(readOnly = true)
  public IPage<GroundTruthVO> truthPage(long current, long size, UUID sampleId) {
    var query = Wrappers.<GroundTruthPO>lambdaQuery()
        .eq(sampleId != null, GroundTruthPO::getSampleId, sampleId)
        .orderByDesc(GroundTruthPO::getConfirmedAt);
    return truths
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), query)
        .convert(this::truthVo);
  }

  @Transactional(readOnly = true)
  public boolean hasMandatoryReview() {
    var current = CurrentUserContext.required();
    if (!current.hasRole("SECONDARY_REVIEWER"))
      throw new SecurityException("secondary reviewer role required");
    return tasks.selectCount(
            Wrappers.<ReviewTaskPO>lambdaQuery()
                .eq(ReviewTaskPO::getPriority, "P1")
                .in(ReviewTaskPO::getStatus,
                    Status.ESCALATED.name(), Status.PENDING.name(), Status.IN_REVIEW.name()))
        > 0;
  }

  private void require(ReviewTaskPO p, long version, Status... allowedStatuses) {
    boolean validStatus = p != null && Arrays.stream(allowedStatuses)
        .anyMatch(status -> status.name().equals(p.status));
    if (!validStatus || p.version != version)
      throw new IllegalStateException("task state/version conflict");
  }

  private ReviewTaskVO vo(ReviewTaskPO p) {
    return new ReviewTaskVO(
        p.id,
        p.sampleId,
        sampleNo(p.sampleId),
        p.priority,
        p.consistency,
        Status.valueOf(p.status),
        p.ownerId,
        p.processInstanceId,
        p.sourceTargetsJson,
        p.version);
  }

  private String sampleNo(UUID sampleId) {
    try {
      var response = samples.getByBusinessId(sampleId);
      return response == null || response.data() == null
          ? "未关联历史样本"
          : response.data().sampleNo();
    } catch (RuntimeException unavailable) {
      return "未关联历史样本";
    }
  }

  private List<ReviewDTO.SourceTarget> readSourceTargets(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return json.readValue(value, json.getTypeFactory().constructCollectionType(List.class, ReviewDTO.SourceTarget.class));
    } catch (Exception ex) {
      throw new IllegalStateException("invalid stored comparison target snapshot", ex);
    }
  }

  private List<ReviewDTO.TruthTarget> readTruthTargets(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return json.readValue(
          value,
          json.getTypeFactory().constructCollectionType(List.class, ReviewDTO.TruthTarget.class));
    } catch (Exception ex) {
      throw new IllegalStateException("invalid stored ground truth snapshot", ex);
    }
  }

  private ReviewDTO.TruthTarget mergeTrustedSources(
      ReviewDTO.TruthTarget verdict, ReviewDTO.SourceTarget source) {
    if (source == null) {
      return verdict;
    }
    return new ReviewDTO.TruthTarget(
        verdict.targetCode(), verdict.label(), verdict.reasonCode(), verdict.remark(),
        source.systemLabel(), source.primaryLabel(), source.aiLabel());
  }

  private GroundTruthVO truthVo(GroundTruthPO truth) {
    var result = new GroundTruthVO();
    result.setId(truth.id);
    result.setSampleId(truth.sampleId);
    result.setSampleNo(sampleNo(truth.sampleId));
    result.setTruthVersion(truth.truthVersion);
    result.setTaskId(truth.taskId);
    result.setReviewerId(truth.reviewerId);
    result.setAuthUserId(truth.authUserId);
    result.setDurationMs(truth.durationMs);
    result.setConfirmedAt(truth.confirmedAt);
    result.setTargets(readTruthTargets(truth.targetsJson).stream().map(this::truthTargetVo).toList());
    return result;
  }

  private GroundTruthVO.Target truthTargetVo(ReviewDTO.TruthTarget target) {
    var result = new GroundTruthVO.Target();
    result.setTargetCode(target.targetCode());
    result.setTruthLabel(target.label());
    result.setReasonCode(target.reasonCode());
    result.setRemark(target.remark());
    result.setSystemLabel(target.systemLabel());
    result.setPrimaryLabel(target.primaryLabel());
    result.setAiLabel(target.aiLabel());
    result.setSystemCorrect(correct(target.systemLabel(), target.label()));
    result.setPrimaryCorrect(correct(target.primaryLabel(), target.label()));
    result.setAiCorrect(correct(target.aiLabel(), target.label()));
    return result;
  }

  private Boolean correct(TruthLabel predicted, TruthLabel truth) {
    return predicted == null ? null : predicted == truth;
  }

}
