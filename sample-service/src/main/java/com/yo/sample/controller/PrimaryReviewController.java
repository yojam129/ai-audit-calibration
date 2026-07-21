package com.yo.sample.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.sample.domain.dto.PrimaryReviewDTO;
import com.yo.sample.domain.vo.PrimaryReviewTaskVO;
import com.yo.sample.service.PrimaryReviewService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/primary-reviews")
@PreAuthorize("hasAuthority('judgement:submit') or hasAuthority('risk:manage')")
public class PrimaryReviewController {
  private final PrimaryReviewService service;
  public PrimaryReviewController(PrimaryReviewService service) { this.service = service; }

  @GetMapping
  public IPage<PrimaryReviewTaskVO> page(@RequestParam(defaultValue="1") long current,
      @RequestParam(defaultValue="20") long size, @RequestParam(required=false) String status) {
    return service.page(current, Math.min(size, 100), status);
  }
  @GetMapping("/{id}") public PrimaryReviewTaskVO detail(@PathVariable long id) { return service.detail(id); }
  @PostMapping("/{id}/claim")
  @PreAuthorize("hasAuthority('judgement:submit')")
  public PrimaryReviewTaskVO claim(@PathVariable long id,
      @RequestBody PrimaryReviewDTO.Claim request) { return service.claim(id, request); }
  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAuthority('judgement:submit')")
  public PrimaryReviewTaskVO submit(@PathVariable long id,
      @Valid @RequestBody PrimaryReviewDTO.Submit request) { return service.submit(id, request); }
}
