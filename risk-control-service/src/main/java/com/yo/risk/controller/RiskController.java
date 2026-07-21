package com.yo.risk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.risk.domain.dto.*;
import com.yo.risk.domain.vo.*;
import com.yo.risk.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/risks")
@PreAuthorize("hasAuthority('risk:manage')")
public class RiskController {
  private final RiskService service;

  public RiskController(RiskService s) {
    service = s;
  }

  @PostMapping("/outcomes")
  public RiskMetricVO record(@Valid @RequestBody ReviewOutcomeDTO x) {
    return service.record(x);
  }

  @GetMapping("/{reviewerId}")
  public RiskMetricVO get(@PathVariable String reviewerId) {
    return service.get(reviewerId);
  }

  @GetMapping
  public IPage<RiskMetricVO> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String level) {
    return service.page(current, Math.min(size, 100), level);
  }

  @GetMapping("/errors")
  public IPage<ReviewerErrorFocusVO> errors(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String reviewerId) {
    return service.errors(current, Math.min(size, 100), reviewerId);
  }

  @GetMapping("/policy")
  public RiskPolicyVO policy() {
    return service.policy();
  }

  @PutMapping("/policy")
  public RiskPolicyVO updatePolicy(@Valid @RequestBody UpdateRiskPolicyDTO policy) {
    return service.updatePolicy(policy);
  }

  @PostMapping("/{reviewerId}/qualification/reset")
  @PreAuthorize("hasAuthority('internal:call')")
  public void resetQualificationWindow(@PathVariable String reviewerId) {
    service.resetQualificationWindow(reviewerId);
  }
}
