package com.yo.api.client.sample;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.SAMPLE,
    contextId = "sampleClient",
    configuration = FeignInternalConfiguration.class)
public interface SampleClient {
  @PostMapping("/api/samples")
  ApiResponse<SampleVO> create(@RequestBody CreateSampleRequest request);

  @PostMapping("/api/samples/import-aggregate")
  ApiResponse<ImportAggregateVO> importAggregate(@RequestBody ImportAggregateRequest request);

  @GetMapping("/api/samples/{id}")
  ApiResponse<SampleVO> get(@PathVariable("id") long id);

  @GetMapping("/api/samples/{id}/detail")
  ApiResponse<SampleDetailVO> detail(@PathVariable("id") long id);

  @GetMapping("/api/samples/business/{businessId}")
  ApiResponse<SampleVO> getByBusinessId(@PathVariable("businessId") UUID businessId);

  @GetMapping("/api/samples/business/{businessId}/latest-run-no")
  ApiResponse<String> latestRunNo(@PathVariable("businessId") UUID businessId);

  record CreateSampleRequest(
      String organizationId, String externalNo, String specimenType, LocalDateTime collectedAt) {}

  record ImportAggregateRequest(
      String idempotencyKey,
      String organizationId,
      String externalNo,
      String instrumentNo,
      String runNo,
      String cartridgeNo,
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
      List<TargetResult> targets) {}

  record TargetResult(
      String chamber,
      String channelCode,
      String targetCode,
      String systemJudgement,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      List<String> riskFlags) {}

  record ImportAggregateVO(
      long sampleId, long orderId, long runId, String runNo, boolean created) {}

  record SampleVO(
      long id,
      String sampleNo,
      String organizationId,
      String status,
      String specimenType,
      LocalDateTime createdAt) {}

  record SampleDetailVO(SampleVO sample, List<Detection> detections) {}

  record Detection(
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
      List<DetectionTarget> targets) {}

  record DetectionTarget(
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
