package com.yo.sample.mq;

import java.time.Duration;

public final class OutboxRetryPolicy {
  private OutboxRetryPolicy() {}

  public static Duration delay(int attempts, Duration maximum) {
    if (attempts < 1) throw new IllegalArgumentException("attempts must be positive");
    long exponentialSeconds = 1L << Math.min(attempts, 20);
    return Duration.ofSeconds(Math.min(maximum.toSeconds(), exponentialSeconds));
  }
}
