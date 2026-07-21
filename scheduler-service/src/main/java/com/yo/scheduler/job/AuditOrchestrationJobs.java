package com.yo.scheduler.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.yo.scheduler.service.AuditedJobExecutor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditOrchestrationJobs {
  private final AuditedJobExecutor jobs;
  private final boolean scheduledTrainingEnabled;

  public AuditOrchestrationJobs(
      AuditedJobExecutor jobs,
      @Value("${app.ai-training.enabled:true}") boolean scheduledTrainingEnabled) {
    this.jobs = jobs;
    this.scheduledTrainingEnabled = scheduledTrainingEnabled;
  }

  private String param() {
    return XxlJobHelper.getJobParam();
  }

  @XxlJob("consistencyRecalculationJob")
  public void consistency() {
    jobs.execute(
        "consistency-recalculation",
        "statistics-service",
        "/internal/v1/statistics/recalculate",
        param(),
        Duration.ofMinutes(10));
  }

  @XxlJob("outboxRecoveryJob")
  public void outbox() {
    jobs.execute(
        "outbox-recovery-judgement",
        "judgement-service",
        "/internal/v1/outbox/recover",
        param(),
        Duration.ofMinutes(5));
    jobs.execute(
        "outbox-recovery-review",
        "review-workflow-service",
        "/internal/v1/outbox/recover",
        param(),
        Duration.ofMinutes(5));
    jobs.execute(
        "outbox-recovery-risk",
        "risk-control-service",
        "/internal/v1/outbox/recover",
        param(),
        Duration.ofMinutes(5));
  }

  @XxlJob("importReconciliationJob")
  public void imports() {
    jobs.execute(
        "import-reconciliation",
        "integration-service",
        "/internal/v1/imports/reconcile",
        param(),
        Duration.ofMinutes(15));
  }

  @XxlJob("alertWorkflowReconciliationJob")
  public void alertWorkflows() {
    jobs.execute(
        "alert-workflow-reconciliation",
        "alert-service",
        "/internal/v1/alerts/workflows/reconcile",
        param(),
        Duration.ofMinutes(5));
  }

  @XxlJob("reviewSlaScanJob")
  public void sla() {
    jobs.execute(
        "review-sla-scan",
        "review-workflow-service",
        "/internal/v1/reviews/sla/scan",
        param(),
        Duration.ofMinutes(5));
  }

  @XxlJob("positiveRateAlertJob")
  public void positiveRate() {
    jobs.execute(
        "positive-rate-alert",
        "alert-service",
        "/internal/alerts/positive-rate/recalculate",
        param(),
        Duration.ofMinutes(10));
  }

  @XxlJob("aiTruthIncrementalTrainingJob")
  public void aiTruthIncrementalTraining() {
    triggerAiTruthTraining(param());
  }

  @Scheduled(
      cron = "${app.ai-training.cron:0 0 2 * * *}",
      zone = "${app.ai-training.zone:Asia/Shanghai}")
  public void scheduledAiTruthIncrementalTraining() {
    if (scheduledTrainingEnabled) triggerAiTruthTraining("");
  }

  private void triggerAiTruthTraining(String windowParameter) {
    jobs.execute(
        "ai-secondary-truth-incremental-training",
        "signal-service",
        "/internal/v1/ai-training/incremental",
        windowParameter,
        Duration.ofMinutes(30));
  }
}
