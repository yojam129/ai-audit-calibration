package com.yo.common.exception;

import com.yo.common.domain.vo.ApiResponse;
import com.yo.common.domain.vo.CommonResultCode;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(BizException.class)
  public ApiResponse<Void> handleBiz(BizException ex) {
    return ApiResponse.error(ex.resultCode(), ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getAllErrors().isEmpty()
            ? CommonResultCode.BAD_REQUEST.message()
            : ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
    return ApiResponse.error(CommonResultCode.BAD_REQUEST, message);
  }

  @ExceptionHandler(Exception.class)
  public ApiResponse<Void> handleUnexpected(Exception ex) {
    return ApiResponse.error(CommonResultCode.INTERNAL_ERROR);
  }
}
