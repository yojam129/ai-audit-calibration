package com.yo.risk.mq;

import com.yo.api.client.sample.SampleClient;
import com.yo.risk.domain.dto.ReviewOutcomeDTO;
import com.yo.risk.service.RiskService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class GroundTruthRiskListener {
  private final RiskService service;
  private final SampleClient samples;

  public GroundTruthRiskListener(RiskService service, SampleClient samples) {
    this.service = service;
    this.samples = samples;
  }

  @RabbitListener(queues = "${app.queue.ground-truth-risk:risk.ground-truth.v1}")
  public void consume(GroundTruthConfirmedEvent event) {
    if (event.getOutcomes() == null) return;
    SampleContext sample = sampleContext(event);
    Map<String, List<GroundTruthConfirmedEvent.Outcome>> byReviewer =
        event.getOutcomes().stream()
        .filter(outcome -> "PRIMARY".equals(outcome.getSourceType()))
        .filter(outcome -> outcome.getReviewerId() != null && outcome.getAuthUserId() != null)
        .collect(Collectors.groupingBy(GroundTruthConfirmedEvent.Outcome::getReviewerId));
    byReviewer.forEach((reviewerId, outcomes) -> recordSample(event, outcomes, sample));
  }

  private void recordSample(
      GroundTruthConfirmedEvent event,
      List<GroundTruthConfirmedEvent.Outcome> outcomes,
      SampleContext sample) {
    GroundTruthConfirmedEvent.Outcome reviewer = outcomes.getFirst();
    boolean correct = outcomes.stream().allMatch(this::correct);
    String errorType = outcomes.stream()
        .filter(outcome -> !correct(outcome))
        .map(outcome -> outcome.getPredictedLabel() + "_AS_" + outcome.getTruthLabel())
        .findFirst()
        .orElse("NONE");
    service.record(
        new ReviewOutcomeDTO(
            event.getEventId() + ":PRIMARY_SAMPLE:" + reviewer.getReviewerId(),
            reviewer.getReviewerId(),
            reviewer.getAuthUserId(),
            correct,
            outcomes.stream().mapToLong(GroundTruthConfirmedEvent.Outcome::getDurationMs).max().orElse(0),
            errorType,
            event.getOccurredAt(),
            event.getSampleId() == null ? null : event.getSampleId().toString(),
            sample.sampleNo(),
            null,
            null,
            null,
            null,
            null));
    outcomes.stream()
        .filter(outcome -> !correct(outcome))
        .forEach(outcome -> recordError(event, outcome, sample));
  }

  private void recordError(
      GroundTruthConfirmedEvent event,
      GroundTruthConfirmedEvent.Outcome outcome,
      SampleContext sample) {
    TargetPath path = targetPath(outcome.getTargetCode(), sample.detail());
    service.recordErrorFocus(
        new ReviewOutcomeDTO(
            event.getEventId() + ":" + outcome.getTargetCode(),
            outcome.getReviewerId(),
            outcome.getAuthUserId(),
            false,
            outcome.getDurationMs(),
            outcome.getPredictedLabel() + "_AS_" + outcome.getTruthLabel(),
            event.getOccurredAt(),
            event.getSampleId() == null ? null : event.getSampleId().toString(),
            sample.sampleNo(),
            path.chamber(),
            path.channelCode(),
            path.targetCode(),
            outcome.getPredictedLabel(),
            outcome.getTruthLabel()));
  }

  private boolean correct(GroundTruthConfirmedEvent.Outcome outcome) {
    return java.util.Objects.equals(outcome.getPredictedLabel(), outcome.getTruthLabel());
  }

  private SampleContext sampleContext(GroundTruthConfirmedEvent event) {
    if (event.getSampleId() == null) return new SampleContext(null, null);
    try {
      SampleClient.SampleVO sample = samples.getByBusinessId(event.getSampleId()).data();
      if (sample == null) return new SampleContext(null, null);
      return new SampleContext(sample.sampleNo(), samples.detail(sample.id()).data());
    } catch (RuntimeException ignored) {
      return new SampleContext(null, null);
    }
  }

  private TargetPath targetPath(String sourceCode, SampleClient.SampleDetailVO detail) {
    String chamber = null;
    String targetCode = sourceCode;
    int separator = sourceCode == null ? -1 : sourceCode.indexOf(':');
    if (separator > 0) {
      chamber = sourceCode.substring(0, separator);
      targetCode = sourceCode.substring(separator + 1);
    }
    if (detail != null && detail.detections() != null) {
      String expectedChamber = chamber;
      String expectedTarget = targetCode;
      Optional<SampleClient.DetectionTarget> matched = detail.detections().stream()
          .filter(detection -> detection.targets() != null)
          .flatMap(detection -> detection.targets().stream())
          .filter(target -> expectedTarget.equals(target.targetCode()))
          .filter(target -> expectedChamber == null || expectedChamber.equals(target.chamber()))
          .findFirst();
      if (matched.isPresent()) {
        SampleClient.DetectionTarget target = matched.get();
        return new TargetPath(target.chamber(), target.channelCode(), target.targetCode());
      }
    }
    return new TargetPath(chamber, null, targetCode == null ? "UNKNOWN" : targetCode);
  }

  private record SampleContext(String sampleNo, SampleClient.SampleDetailVO detail) {}

  private record TargetPath(String chamber, String channelCode, String targetCode) {}
}
