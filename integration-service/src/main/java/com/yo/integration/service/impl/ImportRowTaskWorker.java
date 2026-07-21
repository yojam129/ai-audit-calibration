package com.yo.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.yo.integration.domain.po.*;
import com.yo.integration.mapper.*;
import com.yo.integration.service.ImportRecoveryService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ImportRowTaskWorker {
  private final ImportRowTaskMapper tasks;
  private final ImportBatchMapper batches;
  private final ImportErrorMapper errors;
  private final FluorescenceImportProcessor processor;
  private final PositiveRateHistoryImportProcessor positiveRateProcessor;
  private final TransactionTemplate transactions;
  private final int maxAttempts;
  private final ImportRecoveryService recovery;

  public ImportRowTaskWorker(
      ImportRowTaskMapper tasks,
      ImportBatchMapper batches,
      ImportErrorMapper errors,
      FluorescenceImportProcessor processor,
      PositiveRateHistoryImportProcessor positiveRateProcessor,
      ImportRecoveryService recovery,
      PlatformTransactionManager manager,
      @Value("${app.import.max-attempts:5}") int maxAttempts) {
    this.tasks = tasks;
    this.batches = batches;
    this.errors = errors;
    this.processor = processor;
    this.positiveRateProcessor = positiveRateProcessor;
    this.recovery = recovery;
    this.transactions = new TransactionTemplate(manager);
    this.maxAttempts = maxAttempts;
  }

  @Scheduled(fixedDelayString = "${app.import.poll-delay:2000}")
  public void poll() {
    tasks.update(
        null,
        new UpdateWrapper<ImportRowTask>()
            .set("status", "RETRY")
            .set("next_attempt_at", LocalDateTime.now())
            .eq("status", "PROCESSING")
            .lt("updated_at", LocalDateTime.now().minusMinutes(5)));
    List<ImportRowTask> ready =
        tasks.selectList(
            new QueryWrapper<ImportRowTask>()
                .in("status", "READY", "RETRY")
                .le("next_attempt_at", LocalDateTime.now())
                .orderByAsc("id")
                .last("limit 20"));
    ready.forEach(this::run);
  }

  public boolean retry(long batchId, int rowNo) {
    return recovery.resolve(batchId, rowNo, "RETRY", "manual retry");
  }

  private void run(ImportRowTask candidate) {
    Boolean claimed =
        transactions.execute(
            ignored ->
                tasks.update(
                        null,
                        new UpdateWrapper<ImportRowTask>()
                            .set("status", "PROCESSING")
                            .set("updated_at", LocalDateTime.now())
                            .eq("id", candidate.id)
                            .in("status", "READY", "RETRY"))
                    == 1);
    if (!Boolean.TRUE.equals(claimed)) return;
    try {
      ImportBatch batch = batches.selectById(candidate.batchId);
      if (batch == null) throw new IllegalArgumentException("Import batch not found");
      if ("POSITIVE_RATE_HISTORY".equalsIgnoreCase(batch.businessType))
        positiveRateProcessor.execute(candidate);
      else processor.execute(candidate);
      transactions.executeWithoutResult(
          ignored -> {
            candidate.status = "SUCCEEDED";
            candidate.lastError = null;
            candidate.nextAttemptAt = null;
            candidate.updatedAt = LocalDateTime.now();
            tasks.updateById(candidate);
            refreshBatch(candidate.batchId);
          });
    } catch (Exception failure) {
      boolean terminal = candidate.attempts + 1 >= maxAttempts;
      transactions.executeWithoutResult(
          ignored -> {
            candidate.attempts++;
            candidate.lastError = FluorescenceImportProcessor.safe(failure.getMessage());
            candidate.updatedAt = LocalDateTime.now();
            if (candidate.attempts >= maxAttempts) {
              candidate.status = "FAILED";
              candidate.nextAttemptAt = null;
              recordTerminalError(candidate);
            } else {
              candidate.status = "RETRY";
              candidate.nextAttemptAt =
                  LocalDateTime.now()
                      .plusSeconds(Math.min(300, 1L << Math.min(8, candidate.attempts)));
            }
            tasks.updateById(candidate);
            refreshBatch(candidate.batchId);
          });
      if (terminal) recovery.ensureOpen(candidate);
    }
  }

  private void refreshBatch(long batchId) {
    long total = tasks.selectCount(new QueryWrapper<ImportRowTask>().eq("batch_id", batchId));
    long succeeded =
        tasks.selectCount(
            new QueryWrapper<ImportRowTask>().eq("batch_id", batchId).eq("status", "SUCCEEDED"));
    long failed =
        tasks.selectCount(
            new QueryWrapper<ImportRowTask>()
                .eq("batch_id", batchId)
                .in("status", "FAILED", "ABANDONED"));
    ImportBatch batch = batches.selectById(batchId);
    if (batch == null) return;
    batch.totalRows = Math.toIntExact(total);
    batch.successRows = Math.toIntExact(succeeded);
    batch.errorRows = Math.toIntExact(failed);
    batch.status = batchStatus(total, succeeded, failed);
    batch.updatedAt = LocalDateTime.now();
    batches.updateById(batch);
  }

  static String batchStatus(long total, long succeeded, long failed) {
    if (total == 0 || succeeded + failed < total) return "PROCESSING";
    if (failed == 0) return "SUCCEEDED";
    if (succeeded == 0) return "FAILED";
    return "PARTIAL_SUCCESS";
  }

  private void recordTerminalError(ImportRowTask task) {
    ImportError error = new ImportError();
    error.batchId = task.batchId;
    error.rowNo = task.rowNo;
    error.columnName = "ROW";
    error.errorCode = "DOWNSTREAM_FAILED";
    error.errorMessage = task.lastError;
    error.createdAt = LocalDateTime.now();
    errors.insert(error);
  }
}
