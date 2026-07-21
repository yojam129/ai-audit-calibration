package com.yo.integration.mq;

public interface ImportEventPublisher {
  void batchReady(String batchNo);
}
