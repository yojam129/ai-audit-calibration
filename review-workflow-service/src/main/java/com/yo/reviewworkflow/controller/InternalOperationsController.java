package com.yo.reviewworkflow.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yo.api.internal.*;
import com.yo.reviewworkflow.domain.po.*;
import com.yo.reviewworkflow.mapper.ReviewMappers.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalOperationsController {
  private final Task tasks;
  private final Outbox outbox;
  private final String secret;

  public InternalOperationsController(
      Task t, Outbox o, @Value("${internal.hmac.secret:}") String s) {
    tasks = t;
    outbox = o;
    secret = s;
  }

  private void verify(String path, String b, String ts, String sig) {
    InternalHmac.verify(
        "POST", path, ts, sig, (b == null ? "" : b).getBytes(StandardCharsets.UTF_8), secret);
  }

  @PostMapping(InternalOperations.RECOVER_OUTBOX)
  public InternalOperations.OperationResult recover(
      @RequestBody(required = false) String b,
      @RequestHeader("X-Internal-Timestamp") String ts,
      @RequestHeader("X-Internal-Signature") String sig) {
    verify(InternalOperations.RECOVER_OUTBOX, b, ts, sig);
    int n =
        outbox.update(
            null,
            new UpdateWrapper<ReviewOutboxPO>()
                .set("status", "RETRY")
                .set("next_attempt_at", Instant.now())
                .eq("status", "SENDING")
                .lt("next_attempt_at", Instant.now().minusSeconds(300)));
    return new InternalOperations.OperationResult("review-outbox-recovery", n, "stale reset");
  }

  @PostMapping(InternalOperations.SCAN_REVIEW_SLA)
  public InternalOperations.OperationResult sla(
      @RequestBody(required = false) String b,
      @RequestHeader("X-Internal-Timestamp") String ts,
      @RequestHeader("X-Internal-Signature") String sig) {
    verify(InternalOperations.SCAN_REVIEW_SLA, b, ts, sig);
    int n =
        tasks.update(
            null,
            new UpdateWrapper<ReviewTaskPO>()
                .set("status", "SLA_BREACHED")
                .in("status", "PENDING", "IN_REVIEW")
                .lt("sla_due_at", Instant.now()));
    return new InternalOperations.OperationResult(
        "review-sla-scan", n, "overdue tasks marked SLA_BREACHED");
  }
}
