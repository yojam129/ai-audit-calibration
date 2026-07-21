package com.yo.alert.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record PositiveRateRecalculationVO(
    String organizationId,
    String targetCode,
    Instant windowStart,
    Instant windowEnd,
    int numerator,
    int denominator,
    BigDecimal rate,
    int baselineNumerator,
    int baselineDenominator,
    BigDecimal baselineRate,
    BigDecimal deviation,
    boolean alertCreated,
    String reason) {}
