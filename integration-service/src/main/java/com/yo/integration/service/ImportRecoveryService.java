package com.yo.integration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yo.integration.domain.po.FileAsset;
import com.yo.integration.domain.po.ImportBatch;
import com.yo.integration.domain.po.ImportError;
import com.yo.integration.domain.po.ImportRecoveryDecision;
import com.yo.integration.domain.po.ImportRowTask;
import com.yo.integration.infrastructure.FlowableImportClient;
import com.yo.integration.mapper.FileAssetMapper;
import com.yo.integration.mapper.ImportBatchMapper;
import com.yo.integration.mapper.ImportErrorMapper;
import com.yo.integration.mapper.ImportRecoveryDecisionMapper;
import com.yo.integration.mapper.ImportRowTaskMapper;
import com.yo.integration.service.impl.FluorescenceImportProcessor;
import com.yo.security.context.CurrentUserContext;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportRecoveryService {
  private static final String RECOVERY_TASK_KEY = "recoverFailedRow";
  private static final String STARTING = "STARTING";

  private final ImportRowTaskMapper tasks;
  private final ImportBatchMapper batches;
  private final FileAssetMapper assets;
  private final ImportErrorMapper errors;
  private final ImportRecoveryDecisionMapper decisions;
  private final FlowableImportClient flowable;
  private final FluorescenceImportProcessor processor;

  public ImportRecoveryService(
      ImportRowTaskMapper tasks,
      ImportBatchMapper batches,
      FileAssetMapper assets,
      ImportErrorMapper errors,
      ImportRecoveryDecisionMapper decisions,
      FlowableImportClient flowable,
      FluorescenceImportProcessor processor) {
    this.tasks = tasks;
    this.batches = batches;
    this.assets = assets;
    this.errors = errors;
    this.decisions = decisions;
    this.flowable = flowable;
    this.processor = processor;
  }

  @Transactional
  public void ensureOpen(ImportRowTask candidate) {
    ImportRowTask task = tasks.selectById(candidate.id);
    if (task == null || !"FAILED".equals(task.status)) return;
    if (task.processInstanceId != null && task.workflowToken == null) {
      migrateLegacyWorkflow(task);
      task = tasks.selectById(candidate.id);
    }
    if (task.processInstanceId != null) {
      synchronizeTask(task);
      return;
    }
    if (!reserveStart(task)) return;
    ImportBatch batch = batches.selectById(task.batchId);
    if (batch == null) {
      releaseStart(task.id, task.workflowToken);
      return;
    }
    String subject = "import " + batch.batchNo + " row " + task.rowNo;
    try {
      String processId =
          flowable.start(
              "import-row:" + task.id,
              "Import recovery - " + subject,
              workflowVariables(
                  task.workflowToken,
                  "ROW",
                  task.id,
                  subject,
                  Map.of(
                      "rowTaskId", task.id,
                      "batchId", task.batchId,
                      "batchNo", batch.batchNo,
                      "rowNo", task.rowNo,
                      "lastError", task.lastError == null ? "" : task.lastError)));
      FlowableImportClient.TaskInfo active = flowable.task(processId);
      tasks.update(
          null,
          new LambdaUpdateWrapper<ImportRowTask>()
              .set(ImportRowTask::getProcessInstanceId, processId)
              .set(ImportRowTask::getFlowableTaskId, active == null ? null : active.id())
              .set(ImportRowTask::getUpdatedAt, LocalDateTime.now())
              .eq(ImportRowTask::getId, task.id)
              .eq(ImportRowTask::getWorkflowToken, task.workflowToken)
              .eq(ImportRowTask::getStatus, "FAILED")
              .eq(ImportRowTask::getFlowableTaskId, STARTING));
    } catch (RuntimeException unavailable) {
      releaseStart(task.id, task.workflowToken);
    }
  }

  @Transactional
  public void ensureOpen(ImportBatch candidate) {
    ImportBatch batch = batches.selectById(candidate.id);
    if (batch == null || !"FAILED".equals(batch.status)) return;
    if (batch.processInstanceId != null && batch.workflowToken == null) {
      migrateLegacyWorkflow(batch);
      batch = batches.selectById(candidate.id);
    }
    if (batch.processInstanceId != null) {
      synchronizeTask(batch);
      return;
    }
    if (tasks.selectCount(
            new LambdaQueryWrapper<ImportRowTask>().eq(ImportRowTask::getBatchId, batch.id))
        > 0) return;
    if (!reserveStart(batch)) return;
    String subject = "import batch " + batch.batchNo;
    try {
      String processId =
          flowable.start(
              "import-batch:" + batch.id,
              "Import recovery - " + subject,
              workflowVariables(
                  batch.workflowToken,
                  "BATCH",
                  batch.id,
                  subject,
                  Map.of(
                      "batchId", batch.id,
                      "batchNo", batch.batchNo,
                      "lastError", batch.failureReason == null ? "" : batch.failureReason)));
      FlowableImportClient.TaskInfo active = flowable.task(processId);
      batches.update(
          null,
          new LambdaUpdateWrapper<ImportBatch>()
              .set(ImportBatch::getProcessInstanceId, processId)
              .set(ImportBatch::getFlowableTaskId, active == null ? null : active.id())
              .set(ImportBatch::getUpdatedAt, LocalDateTime.now())
              .eq(ImportBatch::getId, batch.id)
              .eq(ImportBatch::getWorkflowToken, batch.workflowToken)
              .eq(ImportBatch::getStatus, "FAILED")
              .eq(ImportBatch::getFlowableTaskId, STARTING));
    } catch (RuntimeException unavailable) {
      releaseBatchStart(batch.id, batch.workflowToken);
    }
  }

  public boolean resolve(long batchId, int rowNo, String resolution, String reason) {
    String normalized = normalizeResolution(resolution);
    ImportRowTask task = find(batchId, rowNo);
    if (task == null) return false;
    if (task.recoveryResolution != null && !"FAILED".equals(task.status))
      return sameResolution(task.recoveryResolution, normalized);
    if (!"FAILED".equals(task.status)) return false;
    ensureOpen(task);
    task = find(batchId, rowNo);
    complete(task == null ? null : task.processInstanceId, normalized, reason);
    ImportRowTask resolved = find(batchId, rowNo);
    return resolved != null && sameResolution(resolved.recoveryResolution, normalized);
  }

  public boolean resolveBatch(long batchId, String resolution, String reason) {
    String normalized = normalizeResolution(resolution);
    ImportBatch batch = batches.selectById(batchId);
    if (batch == null) return false;
    if (batch.recoveryResolution != null && !"FAILED".equals(batch.status))
      return sameResolution(batch.recoveryResolution, normalized);
    if (!"FAILED".equals(batch.status)) return false;
    ensureOpen(batch);
    batch = batches.selectById(batchId);
    complete(batch == null ? null : batch.processInstanceId, normalized, reason);
    ImportBatch resolved = batches.selectById(batchId);
    return resolved != null && sameResolution(resolved.recoveryResolution, normalized);
  }

  @Transactional
  public void applyWorkflowDecision(
      String workflowToken, String failureScope, long subjectId, String resolution) {
    String scope = normalizeScope(failureScope);
    String normalized = normalizeResolution(resolution);
    ImportRecoveryDecision existing = findDecision(workflowToken);
    if (existing != null) {
      requireSameDecision(existing, scope, subjectId, normalized);
      return;
    }

    String processInstanceId = validateActiveSubject(workflowToken, scope, subjectId);
    ImportRecoveryDecision decision = new ImportRecoveryDecision();
    decision.setWorkflowToken(workflowToken);
    decision.setFailureScope(scope);
    decision.setSubjectId(subjectId);
    decision.setResolution(normalized);
    decision.setProcessInstanceId(processInstanceId);
    decision.setResolvedAt(LocalDateTime.now());
    if (decisions.insertIgnore(decision) == 0) {
      requireSameDecision(requireDecision(workflowToken), scope, subjectId, normalized);
      return;
    }

    int changed =
        "ROW".equals(scope)
            ? applyRowDecision(workflowToken, subjectId, normalized)
            : applyBatchDecision(workflowToken, subjectId, normalized);
    if (changed != 1) throw new IllegalStateException("Import recovery state changed concurrently");
  }

  @Transactional
  public boolean resumeBatchRetry(long batchId) {
    int claimed =
        batches.update(
            null,
            new LambdaUpdateWrapper<ImportBatch>()
                .set(ImportBatch::getStatus, "CREATED")
                .set(ImportBatch::getUpdatedAt, LocalDateTime.now())
                .eq(ImportBatch::getId, batchId)
                .eq(ImportBatch::getStatus, "RETRY")
                .eq(ImportBatch::getRecoveryResolution, "RETRY"));
    if (claimed != 1) return false;
    ImportBatch batch = batches.selectById(batchId);
    FileAsset asset = batch == null ? null : assets.selectById(batch.assetId);
    if (batch == null || asset == null) {
      if (batch != null) {
        batch.status = "FAILED";
        batch.failureReason = "File asset not found for authorized retry";
        batch.updatedAt = LocalDateTime.now();
        batches.updateById(batch);
        ensureOpen(batch);
      }
      return false;
    }
    processor.process(batch, asset.bucketName, asset.objectKey);
    if ("FAILED".equals(batch.status)) ensureOpen(batch);
    return true;
  }

  public long reconcile() {
    long changed = 0;
    for (ImportRowTask task :
        tasks.selectList(
            new LambdaQueryWrapper<ImportRowTask>().eq(ImportRowTask::getStatus, "FAILED"))) {
      String before = task.processInstanceId;
      ensureOpen(task);
      ImportRowTask after = tasks.selectById(task.id);
      if (!Objects.equals(before, after == null ? null : after.processInstanceId)) changed++;
    }
    for (ImportBatch batch :
        batches.selectList(
            new LambdaQueryWrapper<ImportBatch>().eq(ImportBatch::getStatus, "FAILED"))) {
      String before = batch.processInstanceId;
      ensureOpen(batch);
      ImportBatch after = batches.selectById(batch.id);
      if (!Objects.equals(before, after == null ? null : after.processInstanceId)) changed++;
    }
    for (ImportBatch batch :
        batches.selectList(
            new LambdaQueryWrapper<ImportBatch>().eq(ImportBatch::getStatus, "RETRY"))) {
      if (resumeBatchRetry(batch.id)) changed++;
    }
    return changed;
  }

  private Map<String, Object> workflowVariables(
      String token,
      String scope,
      long subjectId,
      String subject,
      Map<String, Object> evidence) {
    java.util.HashMap<String, Object> variables = new java.util.HashMap<>(evidence);
    variables.put("failureScope", scope);
    variables.put("subject", subject);
    variables.put("retryCallbackUrl", flowable.callbackUrl(token, scope, subjectId, "RETRY"));
    variables.put("abandonCallbackUrl", flowable.callbackUrl(token, scope, subjectId, "ABANDON"));
    return variables;
  }

  private void complete(String processInstanceId, String resolution, String reason) {
    if (processInstanceId == null) throw new IllegalStateException("Flowable recovery is unavailable");
    FlowableImportClient.TaskInfo active = flowable.task(processInstanceId);
    if (active == null || !RECOVERY_TASK_KEY.equals(active.taskDefinitionKey()))
      throw new IllegalStateException("Flowable recovery task is not active");
    String operator = CurrentUserContext.required().username();
    if (active.assignee() == null || active.assignee().isBlank()) flowable.claim(active.id(), operator);
    else if (!operator.equals(active.assignee()))
      throw new IllegalStateException("Flowable recovery task is assigned to another user");
    flowable.complete(active.id(), resolution, reason);
  }

  private boolean reserveStart(ImportRowTask task) {
    String token = UUID.randomUUID().toString();
    LambdaUpdateWrapper<ImportRowTask> update =
        new LambdaUpdateWrapper<ImportRowTask>()
            .set(ImportRowTask::getWorkflowToken, token)
            .set(ImportRowTask::getRecoveryResolution, null)
            .set(ImportRowTask::getRecoveryResolvedAt, null)
            .set(ImportRowTask::getFlowableTaskId, STARTING)
            .set(ImportRowTask::getUpdatedAt, LocalDateTime.now())
            .eq(ImportRowTask::getId, task.id)
            .eq(ImportRowTask::getStatus, "FAILED")
            .isNull(ImportRowTask::getProcessInstanceId);
    if (task.flowableTaskId == null) update.isNull(ImportRowTask::getFlowableTaskId);
    else update.eq(ImportRowTask::getFlowableTaskId, task.flowableTaskId);
    if (task.workflowToken == null) update.isNull(ImportRowTask::getWorkflowToken);
    else update.eq(ImportRowTask::getWorkflowToken, task.workflowToken);
    if (tasks.update(null, update) != 1) return false;
    task.workflowToken = token;
    task.flowableTaskId = STARTING;
    return true;
  }

  private boolean reserveStart(ImportBatch batch) {
    String token = UUID.randomUUID().toString();
    LambdaUpdateWrapper<ImportBatch> update =
        new LambdaUpdateWrapper<ImportBatch>()
            .set(ImportBatch::getWorkflowToken, token)
            .set(ImportBatch::getRecoveryResolution, null)
            .set(ImportBatch::getRecoveryResolvedAt, null)
            .set(ImportBatch::getFlowableTaskId, STARTING)
            .set(ImportBatch::getUpdatedAt, LocalDateTime.now())
            .eq(ImportBatch::getId, batch.id)
            .eq(ImportBatch::getStatus, "FAILED")
            .isNull(ImportBatch::getProcessInstanceId);
    if (batch.flowableTaskId == null) update.isNull(ImportBatch::getFlowableTaskId);
    else update.eq(ImportBatch::getFlowableTaskId, batch.flowableTaskId);
    if (batch.workflowToken == null) update.isNull(ImportBatch::getWorkflowToken);
    else update.eq(ImportBatch::getWorkflowToken, batch.workflowToken);
    if (batches.update(null, update) != 1) return false;
    batch.workflowToken = token;
    batch.flowableTaskId = STARTING;
    return true;
  }

  private void releaseStart(long taskId, String token) {
    tasks.update(
        null,
        new LambdaUpdateWrapper<ImportRowTask>()
            .set(ImportRowTask::getFlowableTaskId, null)
            .eq(ImportRowTask::getId, taskId)
            .eq(ImportRowTask::getWorkflowToken, token)
            .eq(ImportRowTask::getFlowableTaskId, STARTING));
  }

  private void releaseBatchStart(long batchId, String token) {
    batches.update(
        null,
        new LambdaUpdateWrapper<ImportBatch>()
            .set(ImportBatch::getFlowableTaskId, null)
            .eq(ImportBatch::getId, batchId)
            .eq(ImportBatch::getWorkflowToken, token)
            .eq(ImportBatch::getFlowableTaskId, STARTING));
  }

  private void synchronizeTask(ImportRowTask task) {
    FlowableImportClient.TaskInfo active = flowable.task(task.processInstanceId);
    String taskId = active == null ? null : active.id();
    if (!Objects.equals(task.flowableTaskId, taskId)) {
      tasks.update(
          null,
          new LambdaUpdateWrapper<ImportRowTask>()
              .set(ImportRowTask::getFlowableTaskId, taskId)
              .eq(ImportRowTask::getId, task.id)
              .eq(ImportRowTask::getProcessInstanceId, task.processInstanceId));
    }
  }

  private void synchronizeTask(ImportBatch batch) {
    FlowableImportClient.TaskInfo active = flowable.task(batch.processInstanceId);
    String taskId = active == null ? null : active.id();
    if (!Objects.equals(batch.flowableTaskId, taskId)) {
      batches.update(
          null,
          new LambdaUpdateWrapper<ImportBatch>()
              .set(ImportBatch::getFlowableTaskId, taskId)
              .eq(ImportBatch::getId, batch.id)
              .eq(ImportBatch::getProcessInstanceId, batch.processInstanceId));
    }
  }

  private void migrateLegacyWorkflow(ImportRowTask task) {
    flowable.cancel(task.processInstanceId, "Migrate import recovery to callback-driven v2");
    tasks.update(
        null,
        new LambdaUpdateWrapper<ImportRowTask>()
            .set(ImportRowTask::getProcessInstanceId, null)
            .set(ImportRowTask::getFlowableTaskId, null)
            .eq(ImportRowTask::getId, task.id)
            .eq(ImportRowTask::getStatus, "FAILED")
            .eq(ImportRowTask::getProcessInstanceId, task.processInstanceId)
            .isNull(ImportRowTask::getWorkflowToken));
  }

  private void migrateLegacyWorkflow(ImportBatch batch) {
    flowable.cancel(batch.processInstanceId, "Migrate import recovery to callback-driven v2");
    batches.update(
        null,
        new LambdaUpdateWrapper<ImportBatch>()
            .set(ImportBatch::getProcessInstanceId, null)
            .set(ImportBatch::getFlowableTaskId, null)
            .eq(ImportBatch::getId, batch.id)
            .eq(ImportBatch::getStatus, "FAILED")
            .eq(ImportBatch::getProcessInstanceId, batch.processInstanceId)
            .isNull(ImportBatch::getWorkflowToken));
  }

  private int applyRowDecision(String token, long taskId, String resolution) {
    ImportRowTask task = tasks.selectById(taskId);
    if ("RETRY".equals(resolution)) {
      errors.delete(
          new QueryWrapper<ImportError>()
              .eq("batch_id", task.batchId)
              .eq("row_no", task.rowNo)
              .eq("error_code", "DOWNSTREAM_FAILED"));
    }
    int changed =
        tasks.update(
            null,
            new LambdaUpdateWrapper<ImportRowTask>()
                .set(ImportRowTask::getStatus, "RETRY".equals(resolution) ? "RETRY" : "ABANDONED")
                .set(ImportRowTask::getAttempts, "RETRY".equals(resolution) ? 0 : task.attempts)
                .set(ImportRowTask::getNextAttemptAt,
                    "RETRY".equals(resolution) ? LocalDateTime.now() : null)
                .set(ImportRowTask::getLastError,
                    "RETRY".equals(resolution) ? null : task.lastError)
                .set(ImportRowTask::getRecoveryResolution, resolution)
                .set(ImportRowTask::getRecoveryResolvedAt, LocalDateTime.now())
                .set(ImportRowTask::getProcessInstanceId, null)
                .set(ImportRowTask::getFlowableTaskId, null)
                .set(ImportRowTask::getUpdatedAt, LocalDateTime.now())
                .eq(ImportRowTask::getId, taskId)
                .eq(ImportRowTask::getWorkflowToken, token)
                .eq(ImportRowTask::getStatus, "FAILED"));
    if (changed == 1) refreshBatch(task.batchId);
    return changed;
  }

  private int applyBatchDecision(String token, long batchId, String resolution) {
    return batches.update(
        null,
        new LambdaUpdateWrapper<ImportBatch>()
            .set(ImportBatch::getStatus, "RETRY".equals(resolution) ? "RETRY" : "ABANDONED")
            .set("RETRY".equals(resolution), ImportBatch::getFailureReason, null)
            .set(ImportBatch::getRecoveryResolution, resolution)
            .set(ImportBatch::getRecoveryResolvedAt, LocalDateTime.now())
            .set(ImportBatch::getProcessInstanceId, null)
            .set(ImportBatch::getFlowableTaskId, null)
            .set(ImportBatch::getUpdatedAt, LocalDateTime.now())
            .eq(ImportBatch::getId, batchId)
            .eq(ImportBatch::getWorkflowToken, token)
            .eq(ImportBatch::getStatus, "FAILED"));
  }

  private String validateActiveSubject(String token, String scope, long subjectId) {
    if ("ROW".equals(scope)) {
      ImportRowTask task = tasks.selectById(subjectId);
      if (task == null || !"FAILED".equals(task.status) || !token.equals(task.workflowToken))
        throw new IllegalStateException("Import row recovery callback is stale");
      return task.processInstanceId;
    }
    ImportBatch batch = batches.selectById(subjectId);
    if (batch == null || !"FAILED".equals(batch.status) || !token.equals(batch.workflowToken))
      throw new IllegalStateException("Import batch recovery callback is stale");
    return batch.processInstanceId;
  }

  private void refreshBatch(long batchId) {
    long total = tasks.selectCount(new QueryWrapper<ImportRowTask>().eq("batch_id", batchId));
    long succeeded =
        tasks.selectCount(
            new QueryWrapper<ImportRowTask>().eq("batch_id", batchId).eq("status", "SUCCEEDED"));
    long terminalFailures =
        tasks.selectCount(
            new QueryWrapper<ImportRowTask>()
                .eq("batch_id", batchId)
                .in("status", "FAILED", "ABANDONED"));
    ImportBatch batch = batches.selectById(batchId);
    if (batch == null) return;
    batch.totalRows = Math.toIntExact(total);
    batch.successRows = Math.toIntExact(succeeded);
    batch.errorRows = Math.toIntExact(terminalFailures);
    batch.status =
        succeeded + terminalFailures < total
            ? "PROCESSING"
            : terminalFailures == 0
                ? "SUCCEEDED"
                : succeeded == 0 ? "FAILED" : "PARTIAL_SUCCESS";
    batch.updatedAt = LocalDateTime.now();
    batches.updateById(batch);
  }

  private ImportRecoveryDecision findDecision(String token) {
    return decisions.selectOne(
        new LambdaQueryWrapper<ImportRecoveryDecision>()
            .eq(ImportRecoveryDecision::getWorkflowToken, token)
            .last("LIMIT 1"));
  }

  private ImportRecoveryDecision requireDecision(String token) {
    ImportRecoveryDecision decision = findDecision(token);
    if (decision == null) throw new IllegalStateException("Recovery decision was not persisted");
    return decision;
  }

  private void requireSameDecision(
      ImportRecoveryDecision decision, String scope, long subjectId, String resolution) {
    if (!scope.equals(decision.getFailureScope())
        || !Objects.equals(subjectId, decision.getSubjectId())
        || !resolution.equals(decision.getResolution())) {
      throw new IllegalStateException("Workflow token was already used for another decision");
    }
  }

  private static boolean sameResolution(String actual, String expected) {
    if (expected.equals(actual)) return true;
    throw new IllegalStateException("Import failure already has a different recovery decision");
  }

  private String normalizeResolution(String resolution) {
    String normalized = resolution == null ? "" : resolution.trim().toUpperCase();
    if (!"RETRY".equals(normalized) && !"ABANDON".equals(normalized))
      throw new IllegalArgumentException("resolution must be RETRY or ABANDON");
    return normalized;
  }

  private String normalizeScope(String scope) {
    String normalized = scope == null ? "" : scope.trim().toUpperCase();
    if (!"ROW".equals(normalized) && !"BATCH".equals(normalized))
      throw new IllegalArgumentException("failureScope must be ROW or BATCH");
    return normalized;
  }

  private ImportRowTask find(long batchId, int rowNo) {
    return tasks.selectOne(
        new LambdaQueryWrapper<ImportRowTask>()
            .eq(ImportRowTask::getBatchId, batchId)
            .eq(ImportRowTask::getRowNo, rowNo)
            .last("LIMIT 1"));
  }
}
