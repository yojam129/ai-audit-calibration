package com.yo.learning.controller;

import com.yo.learning.service.LearningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning/workflow/callback/{workflowToken}/{assignmentId}")
public class LearningWorkflowCallbackController {
  private final LearningService service;

  public LearningWorkflowCallbackController(LearningService service) {
    this.service = service;
  }

  @PostMapping("/exam-required")
  public ResponseEntity<Void> examRequired(
      @PathVariable String workflowToken, @PathVariable long assignmentId) {
    service.markExamRequired(assignmentId, workflowToken);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/restore-pending")
  public ResponseEntity<Void> restorePending(
      @PathVariable String workflowToken, @PathVariable long assignmentId) {
    service.markRestorePending(assignmentId, workflowToken);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/restore")
  public ResponseEntity<Void> restore(
      @PathVariable String workflowToken, @PathVariable long assignmentId) {
    service.restorePermission(assignmentId, workflowToken);
    return ResponseEntity.noContent().build();
  }
}
