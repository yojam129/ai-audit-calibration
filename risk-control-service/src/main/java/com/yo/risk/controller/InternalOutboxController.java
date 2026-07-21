package com.yo.risk.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yo.api.internal.*;
import com.yo.risk.domain.po.RiskOutbox;
import com.yo.risk.mapper.RiskOutboxMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalOutboxController {
  private final RiskOutboxMapper mapper;
  private final String secret;

  public InternalOutboxController(RiskOutboxMapper m, @Value("${internal.hmac.secret:}") String s) {
    mapper = m;
    secret = s;
  }

  @PostMapping(InternalOperations.RECOVER_OUTBOX)
  public InternalOperations.OperationResult recover(
      @RequestBody(required = false) String b,
      @RequestHeader("X-Internal-Timestamp") String ts,
      @RequestHeader("X-Internal-Signature") String sig) {
    byte[] body = (b == null ? "" : b).getBytes(StandardCharsets.UTF_8);
    InternalHmac.verify("POST", InternalOperations.RECOVER_OUTBOX, ts, sig, body, secret);
    int n =
        mapper.update(
            null,
            new UpdateWrapper<RiskOutbox>()
                .set("status", "RETRY")
                .set("next_attempt_at", Instant.now())
                .eq("status", "SENDING")
                .lt("next_attempt_at", Instant.now().minusSeconds(300)));
    return new InternalOperations.OperationResult("risk-outbox-recovery", n, "stale reset");
  }
}
