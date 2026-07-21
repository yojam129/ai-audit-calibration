package com.yo.judgement.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.judgement.domain.dto.*;
import com.yo.judgement.domain.vo.*;
import com.yo.judgement.service.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {
  private final ComparisonService service;

  public ComparisonController(ComparisonService s) {
    service = s;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('judgement:submit')")
  public ResponseEntity<ComparisonVO> compare(@RequestBody ComparisonDTO dto) {
    return ResponseEntity.status(201).body(service.compare(dto));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('judgement:submit') or hasAuthority('statistics:view') or hasAuthority('review:handle')")
  public IPage<ComparisonSummaryVO> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) UUID sampleId) {
    return service.page(current, Math.min(size, 100), sampleId);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('judgement:submit') or hasAuthority('statistics:view') or hasAuthority('review:handle')")
  public ComparisonDetailVO detail(@PathVariable UUID id) {
    return service.detail(id);
  }
}
