package com.yo.trace.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yo.trace.domain.po.TraceRecord;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TraceOutboxProjectorTest {
  @Test
  void usesEventIdAsIdempotentDocumentId() {
    TraceRecord record = new TraceRecord();
    record.id = 12L;
    record.eventId = "evt-12";
    record.occurredAt = Instant.EPOCH;
    TraceDocument document = TraceOutboxProjector.toDocument(record);
    assertEquals("evt-12", document.eventId);
    assertEquals(12L, document.recordId);
  }
}
