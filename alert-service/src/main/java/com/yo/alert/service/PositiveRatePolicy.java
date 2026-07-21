package com.yo.alert.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PositiveRatePolicy {
  private PositiveRatePolicy() {}

  public static BigDecimal rate(int numerator, int denominator) {
    if (numerator < 0 || denominator < 0 || numerator > denominator)
      throw new IllegalArgumentException("Invalid positive-rate counts");
    return denominator == 0
        ? BigDecimal.ZERO
        : BigDecimal.valueOf(numerator)
            .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
  }

  public static boolean deviates(
      int currentDenominator,
      BigDecimal currentRate,
      int baselineDenominator,
      BigDecimal baselineRate,
      int minimumDenominator,
      BigDecimal threshold) {
    return currentDenominator >= minimumDenominator
        && baselineDenominator >= minimumDenominator
        && currentRate.subtract(baselineRate).abs().compareTo(threshold) >= 0;
  }
}
