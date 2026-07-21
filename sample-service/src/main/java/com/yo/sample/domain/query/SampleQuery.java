package com.yo.sample.domain.query;

public record SampleQuery(
    String sampleNo, String organizationId, String status, long pageNo, long pageSize) {
  public SampleQuery {
    pageNo = Math.max(1, pageNo);
    pageSize = Math.min(100, Math.max(1, pageSize));
  }

  public SampleQuery(String organizationId, String status, long pageNo, long pageSize) {
    this(null, organizationId, status, pageNo, pageSize);
  }
}
