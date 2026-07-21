package com.yo.trace.domain.vo;

public record ChainVerificationVO(
    boolean valid, long checkedRecords, Long brokenRecordId, String message) {}
