package com.yo.integration.domain.query;

public record ImportQuery(String status, String businessType, long pageNo, long pageSize) {
  public ImportQuery {
    pageNo = Math.max(1, pageNo);
    pageSize = Math.min(100, Math.max(1, pageSize));
  }
}
