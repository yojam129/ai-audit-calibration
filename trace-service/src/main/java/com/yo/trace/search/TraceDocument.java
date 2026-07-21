package com.yo.trace.search;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

@Document(indexName = "ai-audit-trace")
public class TraceDocument {
  @Id public String eventId;
  public Long recordId;
  public String eventType;
  public String aggregateType;
  public String aggregateId;
  public String actorId;
  public String organizationId;
  public String traceId;
  public String payloadJson;
  public String previousHash;
  public String eventHash;
  public Instant occurredAt;
}
