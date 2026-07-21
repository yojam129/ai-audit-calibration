package com.yo.integration.controller;

import com.yo.integration.service.ImportRecoveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integration/workflow/callback/import-recovery")
public class ImportRecoveryWorkflowCallbackController {
  private final ImportRecoveryService recovery;

  public ImportRecoveryWorkflowCallbackController(ImportRecoveryService recovery) {
    this.recovery = recovery;
  }

  @PostMapping("/{workflowToken}/{failureScope}/{subjectId}/{resolution}")
  public ResponseEntity<Void> apply(
      @PathVariable String workflowToken,
      @PathVariable String failureScope,
      @PathVariable long subjectId,
      @PathVariable String resolution) {
    recovery.applyWorkflowDecision(workflowToken, failureScope, subjectId, resolution);
    return ResponseEntity.noContent().build();
  }
}
