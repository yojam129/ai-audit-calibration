package com.yo.risk.domain.vo;

import java.time.Instant;

public record RiskEventVO(
    String eventId,
    String reviewerId,
    String eventType,
    String level,
    Instant occurredAt,
    String reason) {}
