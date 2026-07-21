package com.yo.risk.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.risk.domain.vo.ReviewerErrorFocusVO;
import com.yo.risk.service.RiskService;
import com.yo.security.context.CurrentUserContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/risks/me")
@PreAuthorize("hasAuthority('learning:participate')")
public class ReviewerTrainingFocusController {
  private final RiskService service;

  public ReviewerTrainingFocusController(RiskService service) {
    this.service = service;
  }

  @GetMapping("/errors")
  public IPage<ReviewerErrorFocusVO> errors(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size) {
    return service.errors(
        current, Math.min(size, 100), CurrentUserContext.required().username());
  }
}
