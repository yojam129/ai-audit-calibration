package com.yo.trace.domain.query;

import java.time.Instant;

public record TraceQuery(
    String aggregateType,
    String aggregateId,
    String sampleId,
    String orderId,
    String actorId,
    String eventType,
    String keyword,
    Instant from,
    Instant to,
    int page,
    int size) {}
