package com.yo.alert.controller;

import com.yo.alert.domain.vo.*;
import com.yo.alert.service.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import com.yo.security.context.CurrentUserContext;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {
  private final AlertService service;
  private final PositiveRateService positiveRateService;

  public AlertController(AlertService s, PositiveRateService positiveRateService) {
    service = s;
    this.positiveRateService = positiveRateService;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('alert:handle') or hasAuthority('statistics:view')")
  public List<AlertVO> list() {
    return service.list();
  }

  @GetMapping("/positive-rate")
  @PreAuthorize("hasAuthority('alert:handle') or hasAuthority('statistics:view')")
  public List<PositiveRateAlertVO> positiveRateAlerts() {
    return positiveRateService.listAlerts();
  }

  @PostMapping("/{id}/claim")
  @PreAuthorize("hasAuthority('alert:handle')")
  public AlertVO claim(@PathVariable UUID id, @RequestBody ClaimDTO d) {
    return service.claim(id, CurrentUserContext.required().username(), d.expectedVersion());
  }

  public record ClaimDTO(String ownerId, long expectedVersion) {}
}
