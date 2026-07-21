package com.yo.reviewworkflow.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.reviewworkflow.domain.dto.*;
import com.yo.reviewworkflow.domain.po.*;
import com.yo.reviewworkflow.domain.vo.*;
import com.yo.reviewworkflow.service.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
  private final ReviewService service;

  public ReviewController(ReviewService s) {
    service = s;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('review:handle')")
  public ResponseEntity<ReviewTaskVO> create(@RequestBody ReviewDTO.Create d) {
    return ResponseEntity.status(201).body(service.create(d));
  }

  @PostMapping("/{id}/claim")
  @PreAuthorize("hasAuthority('review:handle')")
  public ReviewTaskVO claim(@PathVariable UUID id, @RequestBody ReviewDTO.Claim d) {
    return service.claim(id, d);
  }

  @PostMapping("/{id}/finalize")
  @PreAuthorize("hasAuthority('review:handle')")
  public GroundTruthPO finish(@PathVariable UUID id, @RequestBody ReviewDTO.Finalize d) {
    return service.finalizeTask(id, d);
  }

  @GetMapping
  @PreAuthorize("hasAuthority('review:handle') or hasAuthority('statistics:view')")
  public IPage<ReviewTaskVO> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String status) {
    return service.page(current, Math.min(size, 100), status);
  }

  @GetMapping("/mandatory")
  @PreAuthorize("hasRole('SECONDARY_REVIEWER')")
  public boolean mandatory() {
    return service.hasMandatoryReview();
  }

  @GetMapping("/truths")
  @PreAuthorize("hasAuthority('review:handle') or hasAuthority('statistics:view')")
  public IPage<GroundTruthVO> truths(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) UUID sampleId) {
    return service.truthPage(current, Math.min(size, 100), sampleId);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('review:handle') or hasAuthority('statistics:view')")
  public ReviewTaskVO detail(@PathVariable UUID id) {
    return service.detail(id);
  }
}
