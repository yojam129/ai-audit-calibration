package com.yo.sample.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public record ImportAggregateDTO(
    @NotBlank String idempotencyKey,
    @NotBlank String organizationId,
    @NotBlank String externalNo,
    @NotBlank String instrumentNo,
    @NotBlank String runNo,
    @NotBlank String cartridgeNo,
    String reagentLotNo,
    String panelCode,
    String modulePosition,
    String instrumentType,
    String qcStatus,
    String qcEvidenceJson,
    String targetMappingJson,
    String overallResultJson,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    List<@Valid TargetResultDTO> targets) {
  public record TargetResultDTO(
      @NotBlank String chamber,
      @NotBlank String channelCode,
      @NotBlank String targetCode,
      @NotBlank String systemJudgement,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      List<String> riskFlags) {}
}
