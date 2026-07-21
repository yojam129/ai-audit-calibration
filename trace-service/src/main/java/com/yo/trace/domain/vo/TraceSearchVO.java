package com.yo.trace.domain.vo;

import java.time.Instant;

public record TraceSearchVO(
    long id,
    String eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    String actorId,
    String organizationId,
    String traceId,
    String eventHash,
    Instant occurredAt) {}
