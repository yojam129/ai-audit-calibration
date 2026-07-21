package com.yo.trace.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.yo.trace.domain.po.TraceRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TraceServiceImplTest {
  @Test
  void sha256IsStable() {
    assertEquals(64, TraceServiceImpl.sha("audit").length());
  }

  @Test
  void hashCoversPreviousHashAndBusinessFields() {
    TraceRecord record = new TraceRecord();
    record.previousHash = TraceServiceImpl.genesisHash();
    record.eventId = "event-1";
    record.eventType = "SAMPLE_IMPORTED";
    record.aggregateType = "SAMPLE";
    record.aggregateId = "sample-1";
    record.actorId = "user-1";
    record.organizationId = "org-1";
    record.traceId = "trace-1";
    record.payloadJson = "{\"status\":\"ok\"}";
    record.occurredAt = Instant.parse("2026-07-19T00:00:00Z");
    String original = TraceServiceImpl.calculateHash(record);
    record.payloadJson = "{\"status\":\"changed\"}";
    assertNotEquals(original, TraceServiceImpl.calculateHash(record));
  }
}
