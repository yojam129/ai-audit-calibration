package com.yo.security.domain;

public record TokenPairVO(
    String tokenType,
    String accessToken,
    long accessExpiresIn,
    String refreshToken,
    long refreshExpiresIn) {
  public TokenPairVO {
    tokenType = tokenType == null ? "Bearer" : tokenType;
  }
}
