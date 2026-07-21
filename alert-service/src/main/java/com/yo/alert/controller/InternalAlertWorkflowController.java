package com.yo.alert.controller;

import com.yo.api.internal.InternalHmac;
import com.yo.api.internal.InternalOperations;
import com.yo.alert.service.AlertService;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAlertWorkflowController {
  private static final String PATH = "/internal/v1/alerts/workflows/reconcile";

  private final AlertService alerts;
  private final String secret;

  public InternalAlertWorkflowController(
      AlertService alerts, @Value("${security.internal-call.secret:}") String secret) {
    this.alerts = alerts;
    this.secret = secret;
  }

  @PostMapping(PATH)
  public InternalOperations.OperationResult reconcile(
      @RequestBody(required = false) String body,
      @RequestHeader("X-Internal-Timestamp") String timestamp,
      @RequestHeader("X-Internal-Signature") String signature) {
    String payload = body == null ? "" : body;
    InternalHmac.verify("POST", PATH, timestamp, signature,
        payload.getBytes(StandardCharsets.UTF_8), secret);
    long changed = alerts.reconcileWorkflowLinks();
    return new InternalOperations.OperationResult(
        "alert-workflow-reconciliation", changed,
        "alert projections synchronized from Flowable secondary review");
  }
}
