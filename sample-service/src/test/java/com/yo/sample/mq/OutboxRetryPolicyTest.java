package com.yo.sample.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxRetryPolicyTest {
  @Test
  void growsExponentiallyAndCapsAtConfiguredMaximum() {
    assertThat(OutboxRetryPolicy.delay(1, Duration.ofMinutes(5))).isEqualTo(Duration.ofSeconds(2));
    assertThat(OutboxRetryPolicy.delay(5, Duration.ofMinutes(5))).isEqualTo(Duration.ofSeconds(32));
    assertThat(OutboxRetryPolicy.delay(20, Duration.ofMinutes(5))).isEqualTo(Duration.ofMinutes(5));
  }
}
