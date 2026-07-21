package com.yo.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositiveRatePolicyTest {
  @Test
  void calculatesOnlyFromActualNumeratorAndDenominator() {
    assertThat(PositiveRatePolicy.rate(3, 10)).isEqualByComparingTo("0.300000");
    assertThatThrownBy(() -> PositiveRatePolicy.rate(11, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void refusesAlertWhenEitherWindowHasInsufficientDenominator() {
    assertThat(
            PositiveRatePolicy.deviates(
                29,
                new BigDecimal("0.8"),
                100,
                new BigDecimal("0.2"),
                30,
                new BigDecimal("0.15")))
        .isFalse();
  }

  @Test
  void alertsAtConfiguredAbsoluteDeviation() {
    assertThat(
            PositiveRatePolicy.deviates(
                50,
                new BigDecimal("0.40"),
                60,
                new BigDecimal("0.20"),
                30,
                new BigDecimal("0.15")))
        .isTrue();
  }
}
