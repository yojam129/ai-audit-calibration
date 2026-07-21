package com.yo.learning.mq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LearningOutboxRelayTest {
  @Test
  void retryDelayIsExponentialAndBounded() {
    assertThat(LearningOutboxRelay.retryDelay(1, Duration.ofMinutes(5)))
        .isEqualTo(Duration.ofSeconds(2));
    assertThat(LearningOutboxRelay.retryDelay(5, Duration.ofMinutes(5)))
        .isEqualTo(Duration.ofSeconds(32));
    assertThat(LearningOutboxRelay.retryDelay(20, Duration.ofMinutes(5)))
        .isEqualTo(Duration.ofMinutes(5));
  }
}
