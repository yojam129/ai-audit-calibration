package com.yo.alert.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record PositiveRateAlertVO(
    long id,
    String organizationId,
    String targetCode,
    Instant windowStart,
    Instant windowEnd,
    int numerator,
    int denominator,
    BigDecimal positiveRate,
    int baselineNumerator,
    int baselineDenominator,
    BigDecimal baselineRate,
    BigDecimal deviation,
    String level,
    String status,
    Instant createdAt) {}
