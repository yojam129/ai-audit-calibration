package com.yo.mq.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EventEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    String aggregateId,
    String orgId,
    String traceId,
    Instant occurredAt,
    Map<String, Object> payload) {
  public static EventEnvelope create(
      String type, String aggregateId, String orgId, String traceId, Map<String, Object> payload) {
    return new EventEnvelope(
        UUID.randomUUID(),
        type,
        1,
        aggregateId,
        orgId,
        traceId,
        Instant.now(),
        Map.copyOf(payload));
  }
}
