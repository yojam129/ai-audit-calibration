package com.yo.signal.job;

import com.yo.signal.service.SignalService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AiInferenceRecoveryJob {
  private final SignalService signals;
  private final int batchSize;

  public AiInferenceRecoveryJob(
      SignalService signals,
      @Value("${app.ai-inference.recovery-batch-size:25}") int batchSize) {
    this.signals = signals;
    this.batchSize = batchSize;
  }

  @Scheduled(
      initialDelayString = "${app.ai-inference.recovery-initial-delay:15000}",
      fixedDelayString = "${app.ai-inference.recovery-delay:10000}")
  public void recover() {
    signals.reprocessPendingBatch(batchSize);
  }
}
