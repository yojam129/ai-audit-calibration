package com.yo.api.client.risk;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.RISK,
    contextId = "riskClient",
    configuration = FeignInternalConfiguration.class)
public interface RiskClient {
  @GetMapping("/api/v1/risks/{reviewerId}")
  RiskMetricVO get(@PathVariable("reviewerId") String reviewerId);

  @PostMapping("/api/v1/risks/{reviewerId}/qualification/reset")
  void resetQualificationWindow(@PathVariable("reviewerId") String reviewerId);

  record RiskMetricVO(
      String reviewerId,
      BigDecimal accuracy,
      BigDecimal averageDuration,
      String riskLevel,
      boolean trainingRequired) {}
}
