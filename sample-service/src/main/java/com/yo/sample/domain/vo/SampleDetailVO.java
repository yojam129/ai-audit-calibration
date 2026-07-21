package com.yo.sample.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

public record SampleDetailVO(SampleVO sample, List<Detection> detections) {
  public record Detection(
      Long orderId,
      String orderNo,
      String assayCode,
      String orderStatus,
      Long runId,
      String runNo,
      String instrumentNo,
      String modulePosition,
      String panelCode,
      String instrumentType,
      String runStatus,
      String qcStatus,
      String qcEvidenceJson,
      String targetMappingJson,
      String overallResultJson,
      LocalDateTime startedAt,
      LocalDateTime endedAt,
      String cartridgeNo,
      String reagentLotNo,
      List<Target> targets) {}

  public record Target(
      String chamber,
      String channelCode,
      String targetCode,
      String systemJudgement,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      List<String> riskFlags) {}
}
