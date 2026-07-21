package com.yo.signal.service;

import com.yo.api.client.model.ModelRegistryClient;
import com.yo.signal.adapter.AiTrainingClient;
import com.yo.signal.domain.po.AiTrainingFeedback;
import com.yo.signal.mapper.AiTrainingFeedbackRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiFeedbackTrainingService {
  private final AiTrainingFeedbackRepository feedback;
  private final AiTrainingClient trainer;
  private final ModelRegistryClient models;
  private final String modelCode;
  private final Duration defaultWindow;
  private final ZoneId trainingZone;
  private final boolean activate;
  private final int trafficPercent;

  public AiFeedbackTrainingService(
      AiTrainingFeedbackRepository feedback,
      AiTrainingClient trainer,
      ModelRegistryClient models,
      @Value("${app.ai-training.model-code:${app.ai-inference.model-code:curve-classifier}}")
          String modelCode,
      @Value("${app.ai-training.window:7d}") Duration defaultWindow,
      @Value("${app.ai-training.zone:Asia/Shanghai}") String trainingZone,
      @Value("${app.ai-training.activate:true}") boolean activate,
      @Value("${app.ai-training.traffic-percent:100}") int trafficPercent) {
    this.feedback = feedback;
    this.trainer = trainer;
    this.models = models;
    this.modelCode = modelCode;
    this.defaultWindow = defaultWindow;
    this.trainingZone = ZoneId.of(trainingZone);
    this.activate = activate;
    this.trafficPercent = trafficPercent;
  }

  public synchronized TrainingResult train(TrainingWindow requested) {
    Instant from;
    Instant to;
    if (requested == null || (requested.getFrom() == null && requested.getTo() == null)) {
      ZonedDateTime today = ZonedDateTime.now(trainingZone).toLocalDate().atStartOfDay(trainingZone);
      to = today.toInstant();
      from = today.minusDays(1).toInstant();
    } else {
      to = requested.getTo() == null ? Instant.now() : requested.getTo();
      from = requested.getFrom() == null ? to.minus(defaultWindow) : requested.getFrom();
    }
    if (!from.isBefore(to)) throw new IllegalArgumentException("training window must be increasing");
    List<AiTrainingFeedback> rows =
        feedback.findByStatusAndConfirmedAtGreaterThanEqualAndConfirmedAtLessThan(
            "PENDING", from, to).stream()
            .sorted(Comparator.comparing(AiTrainingFeedback::getFeedbackKey))
            .toList();
    if (rows.isEmpty()) return new TrainingResult("SKIPPED", null, null, 0, "no pending AI errors");
    String trainingKey = trainingKey(rows);
    List<AiTrainingFeedback> completed = feedback.findByTrainingKey(trainingKey);
    if (!completed.isEmpty() && completed.stream().allMatch(x -> "TRAINED".equals(x.getStatus()))) {
      return new TrainingResult(
          "ALREADY_COMPLETED", trainingKey, completed.getFirst().getTrainedModelVersion(),
          completed.size(), "idempotent replay");
    }
    String baseVersion = models.current(modelCode).data().version();
    var request = new AiTrainingClient.TrainingRequest();
    request.setTrainingKey(trainingKey);
    request.setModelCode(modelCode);
    request.setBaseModelVersion(baseVersion);
    request.setActivate(activate);
    request.setTrafficPercent(trafficPercent);
    request.setSamples(rows.stream().map(this::sample).toList());
    var response = trainer.train(request);
    if (!"TRAINED".equals(response.getStatus())
        && !"ALREADY_COMPLETED".equals(response.getStatus())) {
      return new TrainingResult(
          response.getStatus(), trainingKey, response.getModelVersion(), rows.size(),
          response.getDetail());
    }
    Instant trainedAt = Instant.now();
    rows.forEach(
        row -> {
          row.setStatus("TRAINED");
          row.setTrainingKey(trainingKey);
          row.setTrainedModelVersion(response.getModelVersion());
          row.setTrainedAt(trainedAt);
        });
    feedback.saveAll(rows);
    return new TrainingResult(
        response.getStatus(), trainingKey, response.getModelVersion(), rows.size(),
        response.getDetail());
  }

  private AiTrainingClient.TrainingSample sample(AiTrainingFeedback row) {
    var sample = new AiTrainingClient.TrainingSample();
    sample.setFeedbackKey(row.getFeedbackKey());
    sample.setSampleId(row.getSampleId());
    sample.setRunNo(row.getRunNo());
    sample.setCurveId(row.getCurveId());
    sample.setChamber(row.getChamber());
    sample.setChannelCode(row.getChannelCode());
    sample.setTargetCode(row.getTargetCode());
    sample.setAiLabel(row.getAiLabel());
    sample.setTruthLabel(row.getTruthLabel());
    sample.setSourceModelVersion(row.getSourceModelVersion());
    sample.setRawValues(row.getRawValues());
    sample.setCtValue(row.getCtValue());
    sample.setConcentrationValue(row.getConcentrationValue());
    sample.setConcentrationUnit(row.getConcentrationUnit());
    sample.setRiskLevel(row.getRiskLevel());
    sample.setRiskFlags(row.getRiskFlags());
    sample.setCurveChecksum(row.getCurveChecksum());
    return sample;
  }

  private String trainingKey(List<AiTrainingFeedback> rows) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      for (AiTrainingFeedback row : rows) {
        digest.update(row.getFeedbackKey().getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
      }
      return modelCode + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (Exception ex) {
      throw new IllegalStateException("cannot create training key", ex);
    }
  }

  @Data
  public static class TrainingWindow {
    private Instant from;
    private Instant to;
  }

  public record TrainingResult(
      String status, String trainingKey, String modelVersion, int sampleCount, String detail) {}
}
