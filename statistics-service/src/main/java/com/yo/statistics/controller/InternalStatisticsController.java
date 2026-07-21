package com.yo.statistics.controller;

import com.yo.statistics.service.StatisticsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/statistics")
public class InternalStatisticsController {
  private final StatisticsService service;

  public InternalStatisticsController(StatisticsService service) {
    this.service = service;
  }

  @PostMapping("/rebuild")
  public StatisticsService.RebuildResult rebuild() {
    return service.rebuild();
  }
}
