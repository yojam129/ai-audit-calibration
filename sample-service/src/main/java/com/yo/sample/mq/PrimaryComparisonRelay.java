package com.yo.sample.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.judgement.JudgementClient;
import com.yo.api.client.signal.SignalClient;
import com.yo.sample.domain.dto.PrimaryReviewDTO;
import com.yo.sample.domain.po.*;
import com.yo.sample.mapper.*;
import com.yo.sample.service.impl.PrimaryReviewServiceImpl;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PrimaryComparisonRelay {
  private final PrimaryReviewTaskMapper tasks;
  private final SampleMapper samples;
  private final InstrumentRunMapper runs;
  private final TargetJudgementMapper systemTargets;
  private final SignalClient signals;
  private final JudgementClient judgements;
  private final ObjectMapper json;

  public PrimaryComparisonRelay(PrimaryReviewTaskMapper tasks, SampleMapper samples,
      InstrumentRunMapper runs, TargetJudgementMapper systemTargets, SignalClient signals,
      JudgementClient judgements, ObjectMapper json) {
    this.tasks = tasks; this.samples = samples; this.runs = runs;
    this.systemTargets = systemTargets; this.signals = signals;
    this.judgements = judgements; this.json = json;
  }

  @Scheduled(fixedDelayString = "${app.primary-review.relay-delay:2000}")
  public void relay() {
    tasks.selectList(Wrappers.<PrimaryReviewTask>lambdaQuery()
        .eq(PrimaryReviewTask::getStatus, "SUBMITTED").last("limit 50"))
        .forEach(this::compare);
  }

  private void compare(PrimaryReviewTask task) {
    try {
      Sample sample = samples.selectById(task.sampleId);
      InstrumentRun run = runs.selectById(task.runId);
      List<PrimaryReviewDTO.TargetVerdict> primary = json.readValue(
          task.targetsJson, new TypeReference<>() {});
      Map<String, PrimaryReviewDTO.TargetVerdict> primaryByCode = new HashMap<>();
      primary.forEach(item -> primaryByCode.put(item.chamber() + "|" + item.targetCode(), item));
      var aiResponse = signals.aiResults(run.runNo);
      if (aiResponse == null || aiResponse.data() == null)
        throw new IllegalStateException("AI inference results are not ready");
      Map<String, SignalClient.AiResult> aiByCode = new HashMap<>();
      aiResponse.data().forEach(item -> aiByCode.put(item.chamber() + "|" + item.targetCode(), item));
      List<TargetJudgement> system =
          systemTargets.selectList(Wrappers.<TargetJudgement>query().eq("run_id", task.runId));
      boolean inferenceIncomplete = system.stream().anyMatch(item -> {
        var ai = aiByCode.get(item.chamber + "|" + item.targetCode);
        return ai == null || !"COMPLETED".equals(ai.status()) || ai.judgement() == null;
      });
      if (inferenceIncomplete)
        throw new IllegalStateException("AI inference is incomplete for one or more targets");
      var targets = system
          .stream().map(item -> {
            String key = item.chamber + "|" + item.targetCode;
            var ai = aiByCode.get(key);
            String aiLabel = PrimaryReviewServiceImpl.normalizeLabel(ai.judgement());
            return new JudgementClient.TargetDecision(item.chamber + ":" + item.targetCode,
                PrimaryReviewServiceImpl.normalizeLabel(item.systemJudgement),
                primaryByCode.get(key).label(), aiLabel,
                ai == null ? null : ai.confidence(), false,
                item.targetCode.toUpperCase(Locale.ROOT).contains("IC"), false);
          }).toList();
      judgements.compare(new JudgementClient.ComparisonRequest(
          UUID.fromString(sample.businessId), task.id, task.reviewerName,
          task.reviewerAuthUserId, task.durationMs == null ? 0 : task.durationMs, targets));
      task.status = "COMPARISON_COMPLETED";
      tasks.updateById(task);
    } catch (Exception ignored) {
      // SUBMITTED remains retryable; comparison endpoint is idempotent by sample/version.
    }
  }
}
