package com.yo.integration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ImportRowTaskWorkerTest {
  @Test
  void batchRemainsProcessingUntilEveryRowIsTerminal() {
    assertEquals("PROCESSING", ImportRowTaskWorker.batchStatus(10, 8, 1));
  }

  @Test
  void terminalSummaryNeverHidesFailures() {
    assertEquals("SUCCEEDED", ImportRowTaskWorker.batchStatus(10, 10, 0));
    assertEquals("PARTIAL_SUCCESS", ImportRowTaskWorker.batchStatus(10, 8, 2));
    assertEquals("FAILED", ImportRowTaskWorker.batchStatus(10, 0, 10));
  }
}
