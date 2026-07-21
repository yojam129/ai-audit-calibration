package com.yo.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yo.trace.archive.AuditArchiveAdapter;
import com.yo.trace.domain.dto.*;
import com.yo.trace.domain.po.*;
import com.yo.trace.domain.query.*;
import com.yo.trace.domain.vo.*;
import com.yo.trace.mapper.*;
import com.yo.trace.search.*;
import com.yo.trace.service.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraceServiceImpl implements TraceService {
  private final TraceMapper mapper;
  private final TraceOutboxMapper outbox;
  private final ElasticsearchOperations elasticsearch;
  private final boolean elasticsearchEnabled;
  private final Optional<AuditArchiveAdapter> archive;

  public TraceServiceImpl(
      TraceMapper m,
      TraceOutboxMapper outbox,
      ElasticsearchOperations elasticsearch,
      Optional<AuditArchiveAdapter> archive,
      @Value("${trace.elasticsearch.enabled:false}") boolean elasticsearchEnabled) {
    mapper = m;
    this.outbox = outbox;
    this.elasticsearch = elasticsearch;
    this.elasticsearchEnabled = elasticsearchEnabled;
    this.archive = archive;
  }

  @Transactional
  public TraceSearchVO append(AppendTraceDTO x) {
    TraceRecord existing =
        mapper.selectOne(
            new QueryWrapper<TraceRecord>().eq("event_id", x.eventId()).last("limit 1"));
    if (existing != null) return vo(existing);
    if (!Integer.valueOf(1).equals(mapper.acquireHashChainLock())) {
      throw new IllegalStateException("Audit hash chain is busy");
    }
    try {
      TraceRecord r = new TraceRecord();
      r.eventId = x.eventId();
      r.eventType = x.eventType();
      r.aggregateType = x.aggregateType();
      r.aggregateId = x.aggregateId();
      r.actorId = x.actorId();
      r.organizationId = x.organizationId();
      r.traceId = x.traceId();
      r.payloadJson = x.payloadJson();
      r.occurredAt = x.occurredAt();
      TraceRecord last = mapper.latest();
      r.previousHash = last == null ? genesisHash() : last.eventHash;
      r.eventHash = calculateHash(r);
      mapper.insert(r);
      TraceOutbox event = new TraceOutbox();
      event.eventId = r.eventId;
      event.traceRecordId = r.id;
      event.status = "NEW";
      event.attempts = 0;
      event.nextAttemptAt = java.time.Instant.now();
      event.createdAt = java.time.Instant.now();
      outbox.insert(event);
      return vo(r);
    } finally {
      mapper.releaseHashChainLock();
    }
  }

  @Transactional(readOnly = true)
  public List<TraceSearchVO> search(TraceQuery q) {
    if (elasticsearchEnabled) {
      try {
        List<TraceSearchVO> projected = searchElasticsearch(q);
        if (!projected.isEmpty()) return projected;
      } catch (RuntimeException ignored) {
        // MySQL is the source of truth and is the required search fallback.
      }
    }
    QueryWrapper<TraceRecord> w = new QueryWrapper<>();
    if (q.aggregateType() != null) w.eq("aggregate_type", q.aggregateType());
    if (q.aggregateId() != null) w.eq("aggregate_id", q.aggregateId());
    if (q.sampleId() != null) {
      w.eq("aggregate_type", "SAMPLE").eq("aggregate_id", q.sampleId());
    }
    if (q.orderId() != null) {
      w.eq("aggregate_type", "ORDER").eq("aggregate_id", q.orderId());
    }
    if (q.actorId() != null) w.eq("actor_id", q.actorId());
    if (q.eventType() != null) w.eq("event_type", q.eventType());
    if (q.from() != null) w.ge("occurred_at", q.from());
    if (q.to() != null) w.le("occurred_at", q.to());
    if (q.keyword() != null && !q.keyword().isBlank()) {
      w.and(
          nested ->
              nested
                  .like("event_id", q.keyword())
                  .or()
                  .like("trace_id", q.keyword())
                  .or()
                  .like("payload_json", q.keyword()));
    }
    w.orderByDesc("occurred_at").last("limit " + Math.min(Math.max(q.size(), 1), 100));
    return mapper.selectList(w).stream().map(this::vo).toList();
  }

  private List<TraceSearchVO> searchElasticsearch(TraceQuery q) {
    int limit = Math.min(Math.max(q.size(), 1), 100);
    var query =
        NativeQuery.builder()
            .withQuery(
                root ->
                    root.bool(
                        bool -> {
                          if (q.aggregateType() != null)
                            bool.filter(
                                f ->
                                    f.term(
                                        t ->
                                            t.field("aggregateType.keyword")
                                                .value(q.aggregateType())));
                          if (q.aggregateId() != null)
                            bool.filter(
                                f ->
                                    f.term(
                                        t ->
                                            t.field("aggregateId.keyword").value(q.aggregateId())));
                          if (q.sampleId() != null) {
                            bool.filter(
                                f -> f.term(t -> t.field("aggregateType.keyword").value("SAMPLE")));
                            bool.filter(
                                f ->
                                    f.term(
                                        t -> t.field("aggregateId.keyword").value(q.sampleId())));
                          }
                          if (q.orderId() != null) {
                            bool.filter(
                                f -> f.term(t -> t.field("aggregateType.keyword").value("ORDER")));
                            bool.filter(
                                f ->
                                    f.term(t -> t.field("aggregateId.keyword").value(q.orderId())));
                          }
                          if (q.actorId() != null)
                            bool.filter(
                                f -> f.term(t -> t.field("actorId.keyword").value(q.actorId())));
                          if (q.eventType() != null)
                            bool.filter(
                                f ->
                                    f.term(t -> t.field("eventType.keyword").value(q.eventType())));
                          if (q.from() != null || q.to() != null)
                            bool.filter(
                                f ->
                                    f.range(
                                        r -> {
                                          r.field("occurredAt");
                                          if (q.from() != null)
                                            r.gte(
                                                co.elastic.clients.json.JsonData.of(
                                                    q.from().toString()));
                                          if (q.to() != null)
                                            r.lte(
                                                co.elastic.clients.json.JsonData.of(
                                                    q.to().toString()));
                                          return r;
                                        }));
                          if (q.keyword() != null && !q.keyword().isBlank())
                            bool.must(
                                m ->
                                    m.multiMatch(
                                        mm ->
                                            mm.query(q.keyword())
                                                .fields("eventId", "traceId", "payloadJson")));
                          return bool;
                        }))
            .withPageable(
                PageRequest.of(
                    Math.max(q.page(), 0), limit, Sort.by(Sort.Direction.DESC, "occurredAt")))
            .build();
    return elasticsearch.search(query, TraceDocument.class).stream()
        .map(hit -> hit.getContent())
        .map(
            document ->
                new TraceSearchVO(
                    document.recordId,
                    document.eventId,
                    document.eventType,
                    document.aggregateType,
                    document.aggregateId,
                    document.actorId,
                    document.organizationId,
                    document.traceId,
                    document.eventHash,
                    document.occurredAt))
        .toList();
  }

  @Transactional(readOnly = true)
  public ChainVerificationVO verifyChain() {
    List<TraceRecord> records = mapper.selectList(new QueryWrapper<TraceRecord>().orderByAsc("id"));
    String previous = genesisHash();
    for (TraceRecord record : records) {
      if (!previous.equals(record.previousHash)
          || !calculateHash(record).equals(record.eventHash)) {
        return new ChainVerificationVO(
            false, records.indexOf(record) + 1L, record.id, "Hash chain mismatch");
      }
      previous = record.eventHash;
    }
    return new ChainVerificationVO(true, records.size(), null, "Hash chain is valid");
  }

  @Transactional(readOnly = true)
  public String archiveManifest() {
    AuditArchiveAdapter adapter =
        archive.orElseThrow(() -> new IllegalStateException("Audit archive is disabled"));
    return adapter.archive(mapper.selectList(new QueryWrapper<TraceRecord>().orderByAsc("id")));
  }

  static String calculateHash(TraceRecord record) {
    return sha(
        String.join(
            "|",
            nullSafe(record.previousHash),
            nullSafe(record.eventId),
            nullSafe(record.eventType),
            nullSafe(record.aggregateType),
            nullSafe(record.aggregateId),
            nullSafe(record.actorId),
            nullSafe(record.organizationId),
            nullSafe(record.traceId),
            nullSafe(record.payloadJson),
            String.valueOf(record.occurredAt)));
  }

  static String genesisHash() {
    return "0".repeat(64);
  }

  private static String nullSafe(String value) {
    return value == null ? "" : value;
  }

  private TraceSearchVO vo(TraceRecord r) {
    return new TraceSearchVO(
        r.id,
        r.eventId,
        r.eventType,
        r.aggregateType,
        r.aggregateId,
        r.actorId,
        r.organizationId,
        r.traceId,
        r.eventHash,
        r.occurredAt);
  }

  static String sha(String s) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
