package com.yo.risk.domain.vo;

import com.yo.risk.enums.RiskLevel;
import java.time.Instant;
import java.util.Map;

public record RiskMetricVO(
    String reviewerId,
    Instant windowStart,
    long reviewed,
    long correct,
    double accuracy,
    int recentReviewed,
    int recentCorrect,
    double recentAccuracy,
    boolean recentWindowReady,
    double averageDurationMs,
    Map<String, Long> errorCounts,
    RiskLevel level,
    boolean trainingRequired) {}
