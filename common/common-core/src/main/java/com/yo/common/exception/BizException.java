package com.yo.common.exception;

import com.yo.common.domain.vo.ResultCode;

public class BizException extends RuntimeException {
  private final ResultCode resultCode;

  public BizException(ResultCode resultCode) {
    super(resultCode.message());
    this.resultCode = resultCode;
  }

  public BizException(ResultCode resultCode, String message) {
    super(message);
    this.resultCode = resultCode;
  }

  public ResultCode resultCode() {
    return resultCode;
  }
}
