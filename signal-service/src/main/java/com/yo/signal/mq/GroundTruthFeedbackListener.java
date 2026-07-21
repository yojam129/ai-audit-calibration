package com.yo.signal.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.yo.api.client.sample.SampleClient;
import com.yo.signal.domain.po.AiInferenceResult;
import com.yo.signal.domain.po.AiTrainingFeedback;
import com.yo.signal.domain.po.CurveDocument;
import com.yo.signal.mapper.AiInferenceResultMapper;
import com.yo.signal.mapper.AiTrainingFeedbackRepository;
import com.yo.signal.mapper.CurveRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
public class GroundTruthFeedbackListener {
  private final SampleClient samples;
  private final CurveRepository curves;
  private final AiInferenceResultMapper results;
  private final AiTrainingFeedbackRepository feedback;

  public GroundTruthFeedbackListener(
      SampleClient samples,
      CurveRepository curves,
      AiInferenceResultMapper results,
      AiTrainingFeedbackRepository feedback) {
    this.samples = samples;
    this.curves = curves;
    this.results = results;
    this.feedback = feedback;
  }

  @RabbitListener(queues = "signal.ai-training-feedback.v1")
  public void consume(GroundTruthEvent event) {
    if (event == null || event.getEventId() == null || event.getSampleId() == null) return;
    String runNo = resolveRunNo(event.getSampleId());
    if (runNo == null) throw new IllegalStateException("instrument run not found for truth sample");
    for (TruthTarget target : event.getTargets()) {
      if (target.getAiLabel() == null
          || target.getLabel() == null
          || target.getAiLabel().equals(target.getLabel())) continue;
      String[] targetParts = target.getTargetCode().split(":", 2);
      String targetCode = targetParts.length == 2 ? targetParts[1] : targetParts[0];
      String chamber = targetParts.length == 2 ? targetParts[0] : null;
      for (CurveDocument curve :
          curves.findByRunNoAndTargetCodeOrderByChamberAscChannelCodeAsc(
              runNo, targetCode)) {
        if (chamber != null && !chamber.equalsIgnoreCase(curve.getChamber())) continue;
        save(event, target, curve);
      }
    }
  }

  private String resolveRunNo(String sampleId) {
    return samples.latestRunNo(UUID.fromString(sampleId)).data();
  }

  private void save(GroundTruthEvent event, TruthTarget target, CurveDocument curve) {
    String key = event.getEventId() + ":" + target.getTargetCode() + ":" + curve.getId();
    if (feedback.existsByFeedbackKey(key)) return;
    AiInferenceResult inference =
        results.selectOne(
            Wrappers.<AiInferenceResult>lambdaQuery()
                .eq(AiInferenceResult::getCurveId, curve.getId())
                .last("limit 1"));
    var row = new AiTrainingFeedback();
    row.setId(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
    row.setFeedbackKey(key);
    row.setEventId(event.getEventId());
    row.setSampleId(event.getSampleId());
    row.setTruthVersion(event.getTruthVersion());
    row.setRunNo(curve.getRunNo());
    row.setCurveId(curve.getId());
    row.setChamber(curve.getChamber());
    row.setChannelCode(curve.getChannelCode());
    row.setTargetCode(target.getTargetCode());
    row.setAiLabel(target.getAiLabel());
    row.setTruthLabel(target.getLabel());
    row.setSourceModelVersion(inference == null ? null : inference.getModelVersion());
    row.setRawValues(List.copyOf(curve.getRawValues()));
    row.setCorrectedValues(List.copyOf(curve.getCorrectedValues()));
    row.setCtValue(curve.getCtValue());
    row.setConcentrationValue(curve.getConcentrationValue());
    row.setConcentrationUnit(curve.getConcentrationUnit());
    row.setRiskLevel(curve.getRiskLevel());
    row.setRiskFlags(curve.getRiskFlags() == null ? List.of() : List.copyOf(curve.getRiskFlags()));
    row.setFeatures(curve.getFeatures());
    row.setCurveChecksum(curve.getChecksum());
    row.setConfirmedAt(event.getOccurredAt());
    row.setStatus("PENDING");
    row.setCreatedAt(Instant.now());
    try {
      feedback.save(row);
    } catch (DuplicateKeyException duplicateDelivery) {
      // Rabbit redelivery is expected; the immutable feedback key makes it idempotent.
    }
  }

  @Data
  public static class GroundTruthEvent {
    private String eventId;
    private String sampleId;
    private long truthVersion;
    private Instant occurredAt;
    private List<TruthTarget> targets = List.of();
  }

  @Data
  public static class TruthTarget {
    private String targetCode;
    private String label;
    private String aiLabel;
  }
}
