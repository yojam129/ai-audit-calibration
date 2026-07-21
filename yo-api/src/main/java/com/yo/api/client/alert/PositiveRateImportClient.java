package com.yo.api.client.alert;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.ALERT,
    contextId = "positiveRateImportClient",
    configuration = FeignInternalConfiguration.class)
public interface PositiveRateImportClient {
  @PostMapping("/internal/alerts/positive-rate/facts")
  ApiResponse<Boolean> importFacts(@RequestBody DetectionFactRequest request);

  record DetectionFactRequest(
      UUID eventId,
      String organizationId,
      String orderId,
      String instrumentNo,
      String panelCode,
      String reagentLotNo,
      Instant occurredAt,
      List<TargetResult> targets) {}

  record TargetResult(
      String targetCode,
      String resultLabel,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel) {}
}
