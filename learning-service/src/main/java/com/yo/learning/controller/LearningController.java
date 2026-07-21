package com.yo.learning.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.learning.domain.dto.*;
import com.yo.learning.domain.vo.*;
import com.yo.learning.service.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/learning")
@PreAuthorize("hasAuthority('learning:participate') or hasAuthority('risk:manage')")
public class LearningController {
  private final LearningService s;

  public LearningController(LearningService s) {
    this.s = s;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('risk:manage')")
  public long assign(@Valid @RequestBody LearningDTO x) {
    return s.assign(x);
  }

  @PostMapping("/{id}/exam")
  public PermissionRestoreApplicationVO exam(@PathVariable long id, @Valid @RequestBody ExamDTO x) {
    return s.exam(id, x);
  }

  @PostMapping("/{id}/exam/start")
  public ExamVO startExam(@PathVariable long id) {
    return s.startExam(id);
  }

  @PostMapping("/{id}/training/complete")
  public PermissionRestoreApplicationVO completeTraining(@PathVariable long id) {
    return s.completeTraining(id);
  }

  @GetMapping
  public IPage<PermissionRestoreApplicationVO> page(
      @RequestParam(defaultValue = "1") long current,
      @RequestParam(defaultValue = "20") long size,
      @RequestParam(required = false) String reviewerId,
      @RequestParam(required = false) String status) {
    return s.page(current, Math.min(size, 100), reviewerId, status);
  }
}
