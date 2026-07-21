package com.yo.api.client.alert;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
    name = ServiceNames.ALERT,
    contextId = "alertClient",
    configuration = FeignInternalConfiguration.class)
public interface AlertClient {
  @PostMapping("/internal/alerts/positive-rate/recalculate")
  ApiResponse<PositiveRateRecalculation> recalculate(
      @RequestParam String organizationId,
      @RequestParam String targetCode,
      @RequestParam Instant windowEnd);

  record PositiveRateRecalculation(
      String organizationId,
      String targetCode,
      Instant windowStart,
      Instant windowEnd,
      int numerator,
      int denominator,
      BigDecimal rate,
      int baselineNumerator,
      int baselineDenominator,
      BigDecimal baselineRate,
      BigDecimal deviation,
      boolean alertCreated,
      String reason) {}
}
