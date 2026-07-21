package com.yo.model.enums;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelStatusTest {
  @Test
  void permitsControlledLifecycleAndRejectsTerminalTransitions() {
    assertThat(ModelStatus.VALIDATED.canTransitionTo(ModelStatus.CANARY)).isTrue();
    assertThat(ModelStatus.CANARY.canTransitionTo(ModelStatus.ACTIVE)).isTrue();
    assertThat(ModelStatus.ACTIVE.canTransitionTo(ModelStatus.ROLLED_BACK)).isTrue();
    assertThat(ModelStatus.ROLLED_BACK.canTransitionTo(ModelStatus.ACTIVE)).isFalse();
    assertThat(ModelStatus.REGISTERED.canTransitionTo(ModelStatus.ACTIVE)).isFalse();
  }
}
