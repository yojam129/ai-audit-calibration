package com.yo.alert.controller;

import com.yo.alert.domain.vo.PositiveRateRecalculationVO;
import com.yo.alert.service.PositiveRateService;
import com.yo.common.domain.vo.ApiResponse;
import java.time.Instant;
import java.util.UUID;
import com.yo.alert.mq.DetectionTargetCompletedEvent;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/alerts/positive-rate")
public class InternalPositiveRateController {
  private final PositiveRateService service;

  public InternalPositiveRateController(PositiveRateService service) {
    this.service = service;
  }

  @PostMapping("/recalculate")
  public ApiResponse<PositiveRateRecalculationVO> recalculate(
      @RequestParam String organizationId,
      @RequestParam String targetCode,
      @RequestParam(required = false) Instant windowEnd) {
    return ApiResponse.ok(
        service.recalculate(
            organizationId, targetCode, windowEnd == null ? Instant.now() : windowEnd));
  }

  @PostMapping("/facts")
  public ApiResponse<Boolean> importFacts(@RequestBody DetectionFactDTO dto) {
    service.consume(new DetectionTargetCompletedEvent(
        dto.eventId(), dto.organizationId(), dto.orderId(), dto.instrumentNo(), dto.panelCode(),
        dto.reagentLotNo(), dto.occurredAt(),
        dto.targets().stream().map(target -> new DetectionTargetCompletedEvent.TargetResult(
            target.targetCode(), target.resultLabel(), target.ctValue(), target.concentrationValue(),
            target.concentrationUnit(), target.riskLevel())).toList()));
    return ApiResponse.ok(true);
  }

  public record DetectionFactDTO(
      UUID eventId, String organizationId, String orderId, String instrumentNo, String panelCode,
      String reagentLotNo, Instant occurredAt, java.util.List<TargetDTO> targets) {}

  public record TargetDTO(
      String targetCode, String resultLabel, Double ctValue, Double concentrationValue,
      String concentrationUnit, String riskLevel) {}
}
