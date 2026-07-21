package com.yo.judgement.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yo.api.internal.*;
import com.yo.judgement.domain.po.OutboxEventPO;
import com.yo.judgement.mapper.OutboxEventMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalOutboxController {
  private final OutboxEventMapper mapper;
  private final String secret;

  public InternalOutboxController(
      OutboxEventMapper m, @Value("${internal.hmac.secret:}") String s) {
    mapper = m;
    secret = s;
  }

  @PostMapping(InternalOperations.RECOVER_OUTBOX)
  public InternalOperations.OperationResult recover(
      @RequestBody(required = false) String body,
      @RequestHeader("X-Internal-Timestamp") String ts,
      @RequestHeader("X-Internal-Signature") String sig) {
    byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
    InternalHmac.verify("POST", InternalOperations.RECOVER_OUTBOX, ts, sig, bytes, secret);
    int n =
        mapper.update(
            null,
            new UpdateWrapper<OutboxEventPO>()
                .set("status", "RETRY")
                .set("next_attempt_at", Instant.now())
                .eq("status", "SENDING")
                .lt("next_attempt_at", Instant.now().minusSeconds(300)));
    return new InternalOperations.OperationResult(
        "judgement-outbox-recovery", n, "stale SENDING reset to RETRY");
  }
}
