package com.yo.integration.domain.vo;

public record ImportErrorVO(int rowNo, String columnName, String errorCode, String message) {}
