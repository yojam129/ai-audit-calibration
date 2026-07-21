package com.yo.common.domain.vo;

public enum CommonResultCode implements ResultCode {
  SUCCESS(0, "success"),
  BAD_REQUEST(40000, "请求参数错误"),
  UNAUTHORIZED(40100, "未登录或令牌无效"),
  FORBIDDEN(40300, "无权访问"),
  NOT_FOUND(40400, "资源不存在"),
  CONFLICT(40900, "资源状态冲突"),
  INTERNAL_ERROR(50000, "系统内部错误");

  private final int code;
  private final String message;

  CommonResultCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  public int code() {
    return code;
  }

  public String message() {
    return message;
  }
}
