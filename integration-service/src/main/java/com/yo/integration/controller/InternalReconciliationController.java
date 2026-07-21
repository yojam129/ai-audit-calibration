package com.yo.integration.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yo.api.internal.*;
import com.yo.integration.domain.po.*;
import com.yo.integration.mapper.*;
import com.yo.integration.service.ImportRecoveryService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InternalReconciliationController {
  private final ImportBatchMapper batches;
  private final ImportErrorMapper errors;
  private final String secret;
  private final ImportRecoveryService recovery;

  public InternalReconciliationController(
      ImportBatchMapper b, ImportErrorMapper e, ImportRecoveryService recovery,
      @Value("${internal.hmac.secret:}") String s) {
    batches = b;
    errors = e;
    secret = s;
    this.recovery = recovery;
  }

  @PostMapping(InternalOperations.RECONCILE_IMPORTS)
  public InternalOperations.OperationResult reconcile(
      @RequestBody(required = false) String body,
      @RequestHeader("X-Internal-Timestamp") String ts,
      @RequestHeader("X-Internal-Signature") String sig) {
    InternalHmac.verify(
        "POST",
        InternalOperations.RECONCILE_IMPORTS,
        ts,
        sig,
        (body == null ? "" : body).getBytes(StandardCharsets.UTF_8),
        secret);
    long changed = 0;
    for (var batch :
        batches.selectList(
            new QueryWrapper<ImportBatch>().in(
                "status",
                "SUCCEEDED",
                "PARTIAL_SUCCESS",
                "FAILED",
                "SUCCESS",
                "PARTIAL"))) {
      int actualErrors =
          Math.toIntExact(
              errors.selectCount(new QueryWrapper<ImportError>().eq("batch_id", batch.id)));
      int success = batch.successRows == null ? 0 : batch.successRows;
      int actualTotal = success + actualErrors;
      if (!java.util.Objects.equals(batch.errorRows, actualErrors)
          || !java.util.Objects.equals(batch.totalRows, actualTotal)) {
        batch.errorRows = actualErrors;
        batch.totalRows = actualTotal;
        batch.status = actualErrors == 0
            ? "SUCCEEDED" : success == 0 ? "FAILED" : "PARTIAL_SUCCESS";
        batch.updatedAt = LocalDateTime.now();
        batches.updateById(batch);
        changed++;
      }
    }
    long recoveries = recovery.reconcile();
    return new InternalOperations.OperationResult(
        "import-reconciliation", changed + recoveries,
        "batch counters reconciled; Flowable recoveries opened=" + recoveries);
  }
}
