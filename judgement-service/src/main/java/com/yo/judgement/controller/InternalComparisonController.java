package com.yo.judgement.controller;

import com.yo.judgement.domain.dto.ComparisonDTO;
import com.yo.judgement.domain.vo.ComparisonVO;
import com.yo.judgement.service.ComparisonService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/judgements/comparisons")
public class InternalComparisonController {
  private final ComparisonService service;
  public InternalComparisonController(ComparisonService service) { this.service = service; }
  @PostMapping public ComparisonVO compare(@RequestBody ComparisonDTO request) {
    return service.compare(request);
  }
}
