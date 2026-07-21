package com.yo.trace.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yo.trace.domain.po.TraceOutbox;
import com.yo.trace.domain.po.TraceRecord;
import com.yo.trace.mapper.TraceMapper;
import com.yo.trace.mapper.TraceOutboxMapper;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trace.elasticsearch.enabled", havingValue = "true")
public class TraceOutboxProjector {
  private final TraceOutboxMapper outbox;
  private final TraceMapper traces;
  private final TraceDocumentRepository documents;

  public TraceOutboxProjector(
      TraceOutboxMapper outbox, TraceMapper traces, TraceDocumentRepository documents) {
    this.outbox = outbox;
    this.traces = traces;
    this.documents = documents;
  }

  @Scheduled(fixedDelayString = "${trace.elasticsearch.project-delay:2000}")
  public void project() {
    outbox
        .selectList(
            new QueryWrapper<TraceOutbox>()
                .in("status", "NEW", "RETRY")
                .le("next_attempt_at", Instant.now())
                .orderByAsc("id")
                .last("limit 100"))
        .forEach(this::projectOne);
  }

  private void projectOne(TraceOutbox event) {
    try {
      TraceRecord record = traces.selectById(event.traceRecordId);
      if (record == null) throw new IllegalStateException("Trace record not found");
      documents.save(toDocument(record));
      event.status = "PUBLISHED";
      event.publishedAt = Instant.now();
      event.lastError = null;
    } catch (Exception failure) {
      event.status = "RETRY";
      event.attempts++;
      event.nextAttemptAt =
          Instant.now().plusSeconds(Math.min(300, 1L << Math.min(8, event.attempts)));
      String message = failure.getMessage();
      event.lastError =
          message == null
              ? failure.getClass().getSimpleName()
              : message.substring(0, Math.min(500, message.length()));
    }
    outbox.updateById(event);
  }

  static TraceDocument toDocument(TraceRecord record) {
    TraceDocument document = new TraceDocument();
    document.eventId = record.eventId;
    document.recordId = record.id;
    document.eventType = record.eventType;
    document.aggregateType = record.aggregateType;
    document.aggregateId = record.aggregateId;
    document.actorId = record.actorId;
    document.organizationId = record.organizationId;
    document.traceId = record.traceId;
    document.payloadJson = record.payloadJson;
    document.previousHash = record.previousHash;
    document.eventHash = record.eventHash;
    document.occurredAt = record.occurredAt;
    return document;
  }
}
