package com.yo.common.domain.vo;

import java.time.Instant;

public record ApiResponse<T>(int code, String message, T data, String traceId, Instant timestamp) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(
        CommonResultCode.SUCCESS.code(),
        CommonResultCode.SUCCESS.message(),
        data,
        TraceContext.traceId(),
        Instant.now());
  }

  public static <T> ApiResponse<T> error(ResultCode resultCode) {
    return new ApiResponse<>(
        resultCode.code(), resultCode.message(), null, TraceContext.traceId(), Instant.now());
  }

  public static <T> ApiResponse<T> error(ResultCode resultCode, String message) {
    return new ApiResponse<>(
        resultCode.code(), message, null, TraceContext.traceId(), Instant.now());
  }
}
