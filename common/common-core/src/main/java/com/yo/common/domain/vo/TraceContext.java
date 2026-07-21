package com.yo.common.domain.vo;

import org.slf4j.MDC;

public final class TraceContext {
  private TraceContext() {}

  public static String traceId() {
    String value = MDC.get("traceId");
    return value == null ? "" : value;
  }
}
