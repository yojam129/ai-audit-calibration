package com.yo.statistics.controller;

import com.yo.statistics.domain.vo.StatisticsVO.*;
import com.yo.statistics.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/statistics")
@PreAuthorize("hasAuthority('statistics:view')")
public class StatisticsController {
  private final StatisticsService service;

  public StatisticsController(StatisticsService s) {
    service = s;
  }

  @GetMapping("/dashboard")
  public Dashboard dashboard() {
    return service.dashboard();
  }

  @GetMapping("/accuracy/trend")
  public java.util.List<TrendPoint> trend(
      @RequestParam(required = false) java.time.LocalDate from,
      @RequestParam(required = false) java.time.LocalDate to) {
    return service.trend(from, to);
  }

  @GetMapping("/inconsistencies")
  public com.baomidou.mybatisplus.core.metadata.IPage<Confusion> inconsistencies(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String sourceType) {
    return service.inconsistencies(current, Math.min(size, 100), sourceType);
  }

  @GetMapping("/inconsistency-details")
  public com.baomidou.mybatisplus.core.metadata.IPage<InconsistencyDetail> inconsistencyDetails(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size) {
    return service.inconsistencyDetails(current, Math.min(size, 100));
  }
}
