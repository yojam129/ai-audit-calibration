package com.yo.learning.mq;

import java.time.Instant;

public record TrainingTriggerEvent(
    String eventId,
    String reviewerId,
    Long authUserId,
    String courseCode,
    String errorType,
    int dueDays,
    String riskLevel,
    long durationMs,
    Instant occurredAt,
    String sampleId,
    String sampleNo,
    String chamber,
    String channelCode,
    String targetCode) {}
