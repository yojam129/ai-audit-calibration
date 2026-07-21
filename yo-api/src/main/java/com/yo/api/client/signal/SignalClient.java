package com.yo.api.client.signal;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = ServiceNames.SIGNAL, contextId = "signalClient", configuration = FeignInternalConfiguration.class)
public interface SignalClient {
  @PostMapping("/api/signals")
  ApiResponse<Object> store(@RequestBody StoreCurveRequest request);

  @GetMapping("/api/signals/ai-results")
  ApiResponse<List<AiResult>> aiResults(@RequestParam("runNo") String runNo);

  record AiResult(
      Long id, String curveId, String runNo, String chamber, String targetCode,
      String status, String judgement, Double confidence, String evidenceJson,
      String modelVersion, String failureReason, java.time.LocalDateTime updatedAt) {}

  record StoreCurveRequest(
      String runNo,
      String chamber,
      String channelCode,
      String targetCode,
      String processingVersion,
      List<Double> rawValues,
      List<Double> correctedValues,
      Double ctValue,
      Double concentrationValue,
      String concentrationUnit,
      String riskLevel,
      List<String> riskFlags) {}
}
