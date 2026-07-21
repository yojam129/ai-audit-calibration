package com.yo.signal.domain.query;

public record SignalQuery(
    String runNo, String chamber, String channelCode, String qcStatus, long pageNo, long pageSize) {
  public SignalQuery {
    pageNo = Math.max(1, pageNo);
    pageSize = Math.min(100, Math.max(1, pageSize));
  }

  public SignalQuery(String runNo, String qcStatus, long pageNo, long pageSize) {
    this(runNo, null, null, qcStatus, pageNo, pageSize);
  }
}
